package zlc.season.rxdownloadproject.activity;

import android.Manifest;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import zlc.season.rxdownload2.RxDownload;
import zlc.season.rxdownload2.entity.DownloadStatus;
import zlc.season.rxdownloadproject.Constants;
import zlc.season.rxdownloadproject.R;
import zlc.season.rxdownloadproject.databinding.ActivityBasicDownloadBinding;
import zlc.season.rxdownloadproject.model.BaseModel;
import zlc.season.rxdownloadproject.model.DownloadController;

import static zlc.season.rxdownload2.function.Utils.dispose;

public class BasicDownloadActivity extends AppCompatActivity {
    private String url1 = Constants.URL;
    private String url2 = Constants.URL_QINIU;
    private String[] urls = new String[]{url1, url2};
    private Disposable[] disposables = new Disposable[2];
    private RxDownload rxDownload;
    private DownloadController[] downloadControllers = new DownloadController[2];
    private ActivityBasicDownloadBinding binding = null;
    private BaseModel baseModel1 = null;
    private BaseModel baseModel2 = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // initData
        baseModel1 = new BaseModel();
        baseModel2 = new BaseModel();
        // initView
        binding = DataBindingUtil.setContentView(this, R.layout.activity_basic_download);
        binding.setItem1(baseModel1);
        binding.setItem2(baseModel2);
        binding.contentBasicDownload.setPresenter(new Presenter());
        setSupportActionBar(binding.toolbar);
        //
        rxDownload = RxDownload.getInstance(this);
        downloadControllers[0] = new DownloadController(binding.contentBasicDownload.status1, binding.contentBasicDownload.action1);
        downloadControllers[0].setState(new DownloadController.Normal());
        downloadControllers[1] = new DownloadController(binding.contentBasicDownload.status2, binding.contentBasicDownload.action2);
        downloadControllers[1].setState(new DownloadController.Normal());
        ;
    }

    public class Presenter {
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.action1:
                    downloadControllers[0].handleClick(new DownloadController.Callback() {
                        @Override
                        public void startDownload() {
                            start(0);
                        }

                        @Override
                        public void pauseDownload() {
                            pause(0);
                        }

                        @Override
                        public void install() {
                            installApk(0);
                        }
                    });
                    break;
                case R.id.action2:
                    downloadControllers[1].handleClick(new DownloadController.Callback() {
                        @Override
                        public void startDownload() {
                            start(1);
                        }

                        @Override
                        public void pauseDownload() {
                            pause(1);
                        }

                        @Override
                        public void install() {
                            installApk(1);
                        }
                    });
                    break;
                case R.id.finish:
                    BasicDownloadActivity.this.finish();
                    break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (Disposable disposable : disposables) {
            dispose(disposable);
        }
    }

    private void start(final int index) {
        RxPermissions.getInstance(this)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .doOnNext(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean aBoolean) throws Exception {
                        if (!aBoolean) {
                            throw new RuntimeException("no permission");
                        }
                    }
                })
                .observeOn(Schedulers.io())
                .compose(rxDownload.<Boolean>transform(urls[index], "", getExternalFilesDir("base/download").getAbsolutePath()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<DownloadStatus>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        disposables[index] = d;
                        downloadControllers[index].setState(new DownloadController.Started());
                    }

                    @Override
                    public void onNext(DownloadStatus status) {
                        if(index == 0){
                            binding.contentBasicDownload.progress1.setIndeterminate(status.isChunked);
                            binding.contentBasicDownload.progress1.setMax((int) status.getTotalSize());
                            binding.contentBasicDownload.progress1.setProgress((int) status.getDownloadSize());
                            baseModel1.setPercent(status.getPercent());
                            baseModel1.setSize(status.getFormatStatusString());
                        } else {
                            binding.contentBasicDownload.progress2.setIndeterminate(status.isChunked);
                            binding.contentBasicDownload.progress2.setMax((int) status.getTotalSize());
                            binding.contentBasicDownload.progress2.setProgress((int) status.getDownloadSize());
                            baseModel2.setPercent(status.getPercent());
                            baseModel2.setSize(status.getFormatStatusString());
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        downloadControllers[index].setState(new DownloadController.Paused());
                    }

                    @Override
                    public void onComplete() {
                        downloadControllers[index].setState(new DownloadController.Completed());
                    }
                });
    }

    private void pause(int index) {
        downloadControllers[index].setState(new DownloadController.Paused());
        dispose(disposables[index]);
    }

    private void installApk(int index) {
        File[] files = rxDownload.getRealFiles("");
        if (files != null) {
            Uri uri = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(this, getApplicationInfo().packageName + ".provider", files[0]);
            } else {
                uri = Uri.fromFile(files[0]);
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            startActivity(intent);
        } else {
            Toast.makeText(this, "File not exists", Toast.LENGTH_SHORT).show();
        }
    }
}
