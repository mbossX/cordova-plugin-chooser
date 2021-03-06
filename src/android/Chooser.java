package com.cyph.cordova;

import android.app.Dialog;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.content.CursorLoader;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.text.TextUtils;
import android.widget.RelativeLayout;
import android.os.Bundle;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.lang.Exception;
import java.net.URI;
import java.security.Permission;
import java.util.List;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.bumptech.glide.BitmapTypeRequest;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.zhihu.matisse.Matisse;
import com.zhihu.matisse.MimeType;
import com.zhihu.matisse.SelectionCreator;
import com.zhihu.matisse.engine.impl.GlideEngine;
import com.zhihu.matisse.internal.entity.CaptureStrategy;

import cn.itvmedia.app.R;

public class Chooser extends CordovaPlugin {
	private static final String ACTION_OPEN = "getFile";
	private static final String ACTION_CLOSE = "dismiss";
	private static final int PICK_FILE_REQUEST = 19901;
	private static final int PERMISSION_REQUEST = 19902;
	private static final String TAG = "Chooser";

	private int width = 1920;
	private int height = 1920;

	public static byte[] getBytesFromInputStream(InputStream is) throws IOException {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		byte[] buffer = new byte[0xFFFF];

		for (int len = is.read(buffer); len != -1; len = is.read(buffer)) {
			os.write(buffer, 0, len);
		}

		return os.toByteArray();
	}

	public static String getDisplayName(ContentResolver contentResolver, Uri uri) {
		String[] projection = { MediaStore.MediaColumns.DISPLAY_NAME };
		Cursor metaCursor = contentResolver.query(uri, projection, null, null, null);

		if (metaCursor != null) {
			try {
				if (metaCursor.moveToFirst()) {
					return metaCursor.getString(0);
				}
			} finally {
				metaCursor.close();
			}
		}

		return "File";
	}

	private CallbackContext callback;

	public void chooseFile(CallbackContext callbackContext, String accept) {
		/*
		 * Intent intent = new Intent(); if (Build.VERSION.SDK_INT < 19) {
		 * intent.setAction(Intent.ACTION_GET_CONTENT); intent.setType(accept); } else {
		 * if (accept == "video/mp4") { intent = new Intent(Intent.ACTION_PICK,
		 * MediaStore.Video.Media.EXTERNAL_CONTENT_URI); } else { intent = new
		 * Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI); }
		 * intent.setDataAndType(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, accept); }
		 * cordova.startActivityForResult(this, intent, Chooser.PICK_FILE_REQUEST);
		 */
		// cordova.startActivityForResult(this, null, 1);
		cordova.setActivityResultCallback(this);
		Matisse matisse = Matisse.from(cordova.getActivity());
		SelectionCreator selector = matisse.choose(MimeType.of(MimeType.MP4, MimeType.PNG, MimeType.JPEG), false);
		selector.capture(true).captureStrategy(new CaptureStrategy(true, "app.itvmedia.cn.fileprovider"));
		if (accept.equalsIgnoreCase("video/mp4")) {
			selector = matisse.choose(MimeType.of(MimeType.MP4), false);
			selector.showSingleMediaType(true);
		} else if (accept.equalsIgnoreCase("image/*")) {
			selector = matisse.choose(MimeType.of(MimeType.PNG, MimeType.JPEG), false);
			selector.capture(true).captureStrategy(new CaptureStrategy(true, "app.itvmedia.cn.fileprovider"));
			selector.showSingleMediaType(true);
		}
		selector.maxSelectable(1);
		selector.restrictOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED).thumbnailScale(0.85f)
				.imageEngine(new GlideEngine()).theme(R.style.Matisse_Dracula).forResult(Chooser.PICK_FILE_REQUEST);

		PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
		pluginResult.setKeepCallback(true);
		callbackContext.sendPluginResult(pluginResult);
	}

	private boolean handlePermission() {
		boolean res = true;
		if (!cordova.hasPermission("android.permission.CAMERA")) {
			cordova.requestPermission(this, Chooser.PERMISSION_REQUEST, "android.permission.CAMERA");
			res = false;
		}
		if (!cordova.hasPermission("android.permission.READ_EXTERNAL_STORAGE")) {
			cordova.requestPermission(this, Chooser.PERMISSION_REQUEST, "android.permission.READ_EXTERNAL_STORAGE");
			res = false;
		}
		if (!cordova.hasPermission("android.permission.WRITE_EXTERNAL_STORAGE")) {
			cordova.requestPermission(this, Chooser.PERMISSION_REQUEST, "android.permission.WRITE_EXTERNAL_STORAGE");
			res = false;
		}
		if (!cordova.hasPermission("android.permission.INTERNET")) {
			cordova.requestPermission(this, Chooser.PERMISSION_REQUEST, "android.permission.INTERNET");
			res = false;
		}
		return res;
	}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
		this.callback = callbackContext;
		try {
			if (action.equals(Chooser.ACTION_OPEN)) {
				if (!this.handlePermission()) {
					callbackContext.error("Execute failed: no permission");
					return true;
				}
				width = 1920;
				height = 1920;
				try {
					width = args.getInt(1);
					height = args.getInt(2);
				} catch (JSONException e) {
					e.printStackTrace();
				}
				this.chooseFile(callbackContext, args.getString(0));
				return true;
			}
		} catch (JSONException err) {
			this.callback.error("Execute failed: " + err.toString());
		}

		return false;
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		if (requestCode == Chooser.PERMISSION_REQUEST) {
			if (resultCode == Activity.RESULT_OK) {
				this.chooseFile(callback, "image/*,video/mp4");
			} else {
				this.callback.error("Execute failed: no permission");
			}
			return;
		}
		final Chooser that = this;

		PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, "start");
		pluginResult.setKeepCallback(true);
		that.callback.sendPluginResult(pluginResult);
		cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				try {
					if (requestCode == Chooser.PICK_FILE_REQUEST && that.callback != null) {
						if (resultCode == Activity.RESULT_OK) {
							// Uri uri = data.getData();
							List<Uri> pathList = Matisse.obtainResult(data);
							List<String> paths = Matisse.obtainPathResult(data);
							Uri uri = pathList.get(0);
							String filePath = paths.get(0);

							if (uri != null) {
								ContentResolver contentResolver = that.cordova.getActivity().getContentResolver();

								JSONObject result = new JSONObject();
								String name = Chooser.getDisplayName(contentResolver, uri);

								String mediaType = contentResolver.getType(uri);
								if (mediaType == null || mediaType.isEmpty()) {
									mediaType = "application/octet-stream";
								}

								String thumbnail;
								int duration = 0;
								int w;
								int h;
								byte[] byteArray = null;
								if (mediaType.indexOf("image") > -1) {
									BitmapTypeRequest<Uri> buri = Glide.with(cordova.getActivity()).load(uri)
											.asBitmap();
									BitmapFactory.Options options = new BitmapFactory.Options();
									options.inJustDecodeBounds = true;
									BitmapFactory.decodeFile(filePath, options);
									result.put("rw", options.outWidth);
									result.put("rh", options.outHeight);

									Bitmap thb = buri.centerCrop().into(128, 128).get();
									thumbnail = that.bitmapToBase64(thb);
									thb.recycle();
									w = that.width;
									h = that.height;
									byteArray = buri.toBytes(Bitmap.CompressFormat.JPEG, 90).centerCrop()
											.into(that.width, that.height).get();
								} else { // video get thumbnail
									MediaMetadataRetriever mmr = new MediaMetadataRetriever();
									mmr.setDataSource(that.cordova.getActivity(), uri);
									String durationStr = mmr
											.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
									if (!TextUtils.isEmpty(durationStr)) {
										duration = Integer.valueOf(durationStr);
									}
									Bitmap bmp = mmr.getFrameAtTime(duration % 1000);
									w = bmp.getWidth();
									h = bmp.getHeight();
									thumbnail = that.bitmapToBase64(that.getThumbnail(bmp));

									byteArray = Chooser.getBytesFromInputStream(contentResolver.openInputStream(uri));
									// base64 = Base64.encodeToString(bytes, Base64.DEFAULT);
									bmp.recycle();
								}
								// result.put("data", base64);
								result.put("w", w);
								result.put("h", h);
								result.put("thumbnail", thumbnail);
								result.put("mediaType", mediaType);
								// result.put("name", name);
								result.put("duration", duration);
								// result.put("length", byteArray.length);
								// result.put("uri", uri.toString());

								PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result.toString());
								pluginResult.setKeepCallback(true);
								that.callback.sendPluginResult(pluginResult);

								int len = byteArray.length;
								do {
									int count = 1024 * 512; // 512k
									if (len < count) {
										count = len;
									}
									byte[] bs = new byte[count];
									System.arraycopy(byteArray, byteArray.length - len, bs, 0, count);
									pluginResult = new PluginResult(PluginResult.Status.OK, bs);
									pluginResult.setKeepCallback(true);
									that.callback.sendPluginResult(pluginResult);
									len -= count;
								} while (len > 0);
								pluginResult = new PluginResult(PluginResult.Status.OK, "end");
								pluginResult.setKeepCallback(true);
								that.callback.sendPluginResult(pluginResult);
								// that.callback.success(result.toString());
							} else {
								that.callback.error("File URI was null.");
							}
						} else if (resultCode == Activity.RESULT_CANCELED) {
							that.callback.success("RESULT_CANCELED");
						} else {
							that.callback.error(resultCode);
						}
					}
				} catch (Exception err) {
					that.callback.error("Failed to read file: " + err.toString());
				}
			}
		});
	}

	private byte[] bitmapToBytes(Bitmap bitmap, int quality) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
		return baos.toByteArray();
	}

	/**
	 * 读取照片旋转角度
	 *
	 * @param inputStream 照片路径
	 * @return 角度
	 */
	private int readPictureDegree(InputStream inputStream) {
		int degree = 0;
		if (Build.VERSION.SDK_INT < 24) {
			return degree;
		}
		try {
			ExifInterface exifInterface = new ExifInterface(inputStream);
			int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION,
					ExifInterface.ORIENTATION_NORMAL);
			// Log.e(TAG, "readPictureDegree: orientation-------->" + orientation);
			switch (orientation) {
			case ExifInterface.ORIENTATION_ROTATE_90:
				degree = 90;
				break;
			case ExifInterface.ORIENTATION_ROTATE_180:
				degree = 180;
				break;
			case ExifInterface.ORIENTATION_ROTATE_270:
				degree = 270;
				break;
			}
		} catch (IOException e) {
			this.callback.error("Execute failed: " + e.toString());
		}
		// Log.e(TAG, "readPictureDegree: degree-origin------->" + degree);
		return degree;
	}

	/**
	 * 旋转图片
	 * 
	 * @param angle  被旋转角度
	 * @param bitmap 图片对象
	 * @return 旋转后的图片
	 */
	private Bitmap rotaingImageView(int angle, Bitmap bitmap) {
		Bitmap returnBm = null;
		// // 根据旋转角度，生成旋转矩阵
		Matrix matrix = new Matrix();
		matrix.postRotate(angle);
		try {
			// 将原始图片按照旋转矩阵进行旋转，并得到新的图片
			returnBm = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
		} catch (OutOfMemoryError e) {
		}
		if (returnBm == null) {
			returnBm = bitmap;
		}
		if (bitmap != returnBm) {
			bitmap.recycle();
		}
		return returnBm;
	}

	private String bitmapToBase64(Bitmap bitmap) {
		String result = null;
		ByteArrayOutputStream baos = null;
		try {
			if (bitmap != null) {
				baos = new ByteArrayOutputStream();
				bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);

				baos.flush();
				baos.close();

				byte[] bitmapBytes = baos.toByteArray();
				result = Base64.encodeToString(bitmapBytes, Base64.DEFAULT);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (baos != null) {
					baos.flush();
					baos.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return result;
	}

	public Bitmap bytesToBimap(byte[] b) {
		if (b.length != 0) {
			return BitmapFactory.decodeByteArray(b, 0, b.length);
		} else {
			return null;
		}
	}

	private Bitmap getThumbnail(Bitmap bitmap) {
		int width = bitmap.getWidth();
		int height = bitmap.getHeight();
		Matrix matrix = new Matrix();
		matrix.postScale(128f / width, 128f / height);
		Bitmap bmp;
		try {
			// 将原始图片按照旋转矩阵进行旋转，并得到新的图片
			bmp = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
		} catch (Exception e) {
			bmp = bitmap;
		}
		return bmp;
	}
}