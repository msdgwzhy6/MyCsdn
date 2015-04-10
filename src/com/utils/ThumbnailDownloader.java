package com.utils;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.widget.ImageView;

public class ThumbnailDownloader<Token> extends HandlerThread {
	// Token��ʾ���ͣ�"����<����>"�Ա�֤�����ڿ���ʹ��Token������Token�Ѿ��Ƕ���õ���һ��

	private static final String TAG = "ThumbnailDownloader";
	private static final int MESSAGE_DOWNLOAD = 0;
	private Handler mHandler; // ��������ͼƬ��ָ��ʹ�������ͼƬ��ָ���ʹ��
	private Handler mResponseHandler; // �������̵߳�Handler,����UI
	private Listener<Token> mListener;
	private Map<Token, String> requestMap = Collections
			.synchronizedMap(new HashMap<Token, String>());
	// ����ImageView��URL�ļ�ֵ�ԣ������̰߳�ȫ��
	private LruCache<String, Bitmap> mMemoryCache;

	// ����ͼƬ���࣬���洢ͼƬ�Ĵ�С����LruCache�趨��ֵ��ϵͳ�Զ��ͷ��ڴ�

	public ThumbnailDownloader(Handler handler) {
		super(TAG);
		mResponseHandler = handler;
		// ����һ����ΪTAG��HandlerThread,��ӵ���Լ�Looper�Ķ����߳�
		// super(TAG) �൱��new HandlerThread(TAG)
		int maxMemory = (int) Runtime.getRuntime().maxMemory(); // ϵͳ��������ڴ�
		int mCacheSize = maxMemory / 8; // �����������ڴ��С
		mMemoryCache = new LruCache<String, Bitmap>(mCacheSize) {
			// ������д�˷�����������Bitmap�Ĵ�С
			@Override
			protected int sizeOf(String key, Bitmap value) {
				return value.getRowBytes() * value.getHeight();
			}
		};

	}

	public interface Listener<Token> { // �ص������������߳���ʵ��
		void onThumbnailDownloaded(Token token, Bitmap thumbnail, String url);
	}

	public void setListener(Listener<Token> listener) {
		mListener = listener;
	}

	@Override
	public void onLooperPrepared() {
		// �ڴ��̵߳�Looper����ѭ��׼��ʱ�����еķ���
		mHandler = new Handler() { // �ڵ�ǰ�߳��½���Handler,ֻ���ڵ�ǰ�߳�����
			@Override
			public void handleMessage(Message message) {
				// �������͹�����ͼƬ������Ϣ������ͼƬ������UI
				if (message.what == MESSAGE_DOWNLOAD) {
					Token token = (Token) message.obj;
					try {
						handleRequest(token);
						// ������Ϣ
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

			}
		};
	}

	private void handleRequest(final Token token) throws IOException {
		final String url = requestMap.get(token);
		final String key = (String) ((ImageView) token).getTag();
		if (url == null||!key.equals(url))
			return;

		byte[] bitmapBytes = new HtmlFetchr().getUrlBytes(url);
		// ����ͼƬ
		final Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0,
				bitmapBytes.length);
		if (key.equals(url))
			mMemoryCache.put(key, bitmap); // ���뻺��

		mResponseHandler.post(new Runnable() {

			@Override
			public void run() {

				// ����UI
				if (requestMap.get(token) != url)
					return;
				requestMap.remove(token);
				mListener.onThumbnailDownloaded(token, bitmap, url);// ����UI
			}
		});
	}

	public void clearQueue() {
		mHandler.removeMessages(MESSAGE_DOWNLOAD);
		requestMap.clear();
	}

	public void queueThumbnail(Token token, String url) {
		// ������ͼƬ�������"ThumbnailDownloader"��Ϣ���У�
		// ��PhotoGalleryFragment�б�����

		requestMap.put(token, url);
		Message message = mHandler.obtainMessage(MESSAGE_DOWNLOAD, token);
		// ��ȡMessage,�����Զ���mHandler����һ��
		// ����һ: what��int�ͣ�����������Ϣ
		// ������: obj������Ϣ���͵�ָ������
		// ������: target��������Ϣ��Handler����������ʹ���Զ���mHandler�󶨣���ȱʡ
		message.sendToTarget(); // ������Ϣ��Ŀ��Handler

	}

	public Bitmap getCacheImage(String key) {
		// ��ȡ�����е�ͼƬ
		Bitmap bitmap = mMemoryCache.get(key);
		return bitmap;
	}

}