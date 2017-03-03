package zlc.season.rxdownload2.entity;

import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.schedulers.Schedulers;
import zlc.season.rxdownload2.RxDownload;
import zlc.season.rxdownload2.db.DataBaseHelper;

import static zlc.season.rxdownload2.entity.DownloadFlag.WAITING;
import static zlc.season.rxdownload2.function.Constant.DOWNLOAD_URL_EXISTS;
import static zlc.season.rxdownload2.function.DownloadEventFactory.completed;
import static zlc.season.rxdownload2.function.DownloadEventFactory.failed;
import static zlc.season.rxdownload2.function.DownloadEventFactory.normal;
import static zlc.season.rxdownload2.function.DownloadEventFactory.paused;
import static zlc.season.rxdownload2.function.DownloadEventFactory.started;
import static zlc.season.rxdownload2.function.DownloadEventFactory.waiting;
import static zlc.season.rxdownload2.function.Utils.deleteFiles;
import static zlc.season.rxdownload2.function.Utils.dispose;
import static zlc.season.rxdownload2.function.Utils.formatStr;
import static zlc.season.rxdownload2.function.Utils.getFiles;

/**
 * Author: Season(ssseasonnn@gmail.com)
 * Date: 2017/2/24
 * <p>
 * SingleMission, only one url.
 */
public class SingleMission extends DownloadMission {
    protected DownloadStatus status;
    protected Disposable disposable;
    private DownloadBean bean;

    private MultiMissionCallback callback;
    private String multiMissionId;

    public SingleMission(RxDownload rxdownload, DownloadBean bean) {
        super(rxdownload);
        this.bean = bean;
    }

    public SingleMission(RxDownload rxdownload, DownloadBean bean,
                         String multiMissionId, MultiMissionCallback callback) {
        super(rxdownload);
        this.bean = bean;
        this.multiMissionId = multiMissionId;
        this.callback = callback;
    }

    @Override
    public String getMissionId() {
        return bean.getUrl();
    }

    @Override
    public void init(Map<String, DownloadMission> missionMap,
                     Map<String, FlowableProcessor<DownloadEvent>> processorMap) {
        DownloadMission mission = missionMap.get(getMissionId());
        if (mission != null && !mission.isCancel()) {
            throw new IllegalArgumentException(formatStr(DOWNLOAD_URL_EXISTS, getMissionId()));
        } else {
            missionMap.put(getMissionId(), this);
        }
        processor = getProcessor(processorMap, getMissionId());
    }

    @Override
    public void insertOrUpdate(DataBaseHelper dataBaseHelper) {
        if (dataBaseHelper.recordNotExists(getMissionId())) {
            dataBaseHelper.insertRecord(bean, WAITING, multiMissionId);
        } else {
            dataBaseHelper.updateRecord(getMissionId(), WAITING, multiMissionId);
        }
    }

    @Override
    public void sendWaitingEvent(DataBaseHelper dataBaseHelper) {
        processor.onNext(waiting(dataBaseHelper.readStatus(getMissionId())));
    }

    @Override
    public void start(final Semaphore semaphore) throws InterruptedException {
        if (isCancel()) {
            return;
        }

        semaphore.acquire();

        if (isCancel()) {
            semaphore.release();
            return;
        }

        disposable = rxdownload.download(bean)
                .subscribeOn(Schedulers.io())
                .doOnSubscribe(new Consumer<Disposable>() {
                    @Override
                    public void accept(Disposable disposable) throws Exception {
                        if (callback != null) {
                            callback.start();
                        }
                    }
                })
                .doFinally(new Action() {
                    @Override
                    public void run() throws Exception {
                        semaphore.release();
                    }
                })
                .subscribe(new Consumer<DownloadStatus>() {
                    @Override
                    public void accept(DownloadStatus value) throws Exception {
                        status = value;
                        processor.onNext(started(value));
                        if (callback != null) {
                            callback.next(value);
                        }
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        processor.onNext(failed(status, throwable));
                        if (callback != null) {
                            callback.error(throwable);
                        }
                    }
                }, new Action() {
                    @Override
                    public void run() throws Exception {
                        processor.onNext(completed(status));
                        if (callback != null) {
                            callback.complete();
                        }
                    }
                });
    }

    @Override
    public void pause(DataBaseHelper dataBaseHelper) {
        cancel();
        dispose(disposable);
        processor.onNext(paused(dataBaseHelper.readStatus(getMissionId())));
    }

    @Override
    public void delete(DataBaseHelper dataBaseHelper, boolean deleteFile) {
        pause(dataBaseHelper);
        processor.onNext(normal(null));
        if (deleteFile) {
            DownloadRecord record = dataBaseHelper.readSingleRecord(getMissionId());
            if (record != null) {
                deleteFiles(getFiles(record.getSaveName(), record.getSavePath()));
            }
        }
        dataBaseHelper.deleteRecord(getMissionId());
    }
}