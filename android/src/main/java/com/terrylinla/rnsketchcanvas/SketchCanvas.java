package com.terrylinla.rnsketchcanvas;

import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.graphics.PorterDuffXfermode;
import com.terrylinla.rnsketchcanvas.Utility;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.ArrayList;

import java.io.OutputStream;

public class SketchCanvas extends View {

    private ArrayList<SketchData> mPaths = new ArrayList<SketchData>();
    private SketchData mCurrentPath = null;

    private ThemedReactContext mContext;
    private boolean mDisableHardwareAccelerated = false;

    private Paint mPaint = new Paint();
    private Bitmap mDrawingBitmap = null, mTranslucentDrawingBitmap = null;
    private Canvas mDrawingCanvas = null, mTranslucentDrawingCanvas = null;

    // Mask image for exporting coloring sketch
    private Bitmap mMaskImage;

    private boolean mNeedsFullRedraw = true;

    private int mOriginalWidth, mOriginalHeight;
    private Bitmap mBackgroundImage;
    private Bitmap mForegroundImage;
    private String mContentMode;

    public SketchCanvas(ThemedReactContext context) {
        super(context);
        mContext = context;

    }

    public boolean openImageFile(String backgroundImage, String foregroundImage, String directory, String mode,
            String maskname) {
        BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();

        // get background image if available
        if (backgroundImage != null) {
            // result of image fetch is stored in resBack, '0' means no image was found.
            int resBack = mContext.getResources().getIdentifier(
                    // get filename without extension
                    backgroundImage.lastIndexOf('.') == -1 ? backgroundImage
                            : backgroundImage.substring(0, backgroundImage.lastIndexOf('.')),
                    "drawable",
                    mContext.getPackageName());

            // try to decode the image from the file path
            Bitmap back = resBack == 0
                    ? BitmapFactory.decodeFile(new File(backgroundImage, directory == null ? "" : directory).toString())
                    : BitmapFactory.decodeResource(mContext.getResources(), resBack);

            // save to mBackgroundImage if decode was successful
            if (back != null) {
                mBackgroundImage = back;

                mOriginalHeight = back.getHeight();
                mOriginalWidth = back.getWidth();
                mContentMode = mode;

                invalidateCanvas(true);
            }
        }

        // get foreground image if available
        if (foregroundImage != null) {
            int resFore = mContext.getResources().getIdentifier(
                    foregroundImage.lastIndexOf('.') == -1 ? foregroundImage
                            : foregroundImage.substring(0, foregroundImage.lastIndexOf('.')),
                    "drawable",
                    mContext.getPackageName());

            Bitmap fore = resFore == 0
                    ? BitmapFactory.decodeFile(new File(foregroundImage, directory == null ? "" : directory).toString(),
                            bitmapOptions)
                    : BitmapFactory.decodeResource(mContext.getResources(), resFore);

            if (fore != null) {
                mForegroundImage = fore;

                mOriginalHeight = fore.getHeight();
                mOriginalWidth = fore.getWidth();
                mContentMode = mode;

                invalidateCanvas(true);
            }
        }

        if (maskname != null) {
            int resMask = mContext.getResources().getIdentifier(
                    maskname.lastIndexOf('.') == -1 ? maskname : maskname.substring(0, maskname.lastIndexOf('.')),
                    "drawable",
                    mContext.getPackageName());

            Bitmap mask = resMask == 0
                    ? BitmapFactory.decodeFile(new File(maskname, directory == null ? "" : directory).toString(),
                            bitmapOptions)
                    : BitmapFactory.decodeResource(mContext.getResources(), resMask);

            if (mask != null) {
                mMaskImage = mask;

                mOriginalHeight = mask.getHeight();
                mOriginalWidth = mask.getWidth();
                mContentMode = mode;

                invalidateCanvas(true);
            }
        }

        return true;
    }

    public void clear() {
        mPaths.clear();
        mCurrentPath = null;
        mNeedsFullRedraw = true;
        invalidateCanvas(true);
    }

    public void newPath(int id, int strokeColor, float strokeWidth) {

        mCurrentPath = new SketchData(id, strokeColor, strokeWidth);
        mPaths.add(mCurrentPath);
        boolean isErase = strokeColor == Color.TRANSPARENT;
        if (isErase && mDisableHardwareAccelerated == false) {
            mDisableHardwareAccelerated = true;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        invalidateCanvas(true);

    }

    public void addPoint(float x, float y) {
        Rect updateRect = mCurrentPath.addPoint(new PointF(x, y));

        if (mCurrentPath.isTranslucent) {
            mTranslucentDrawingCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
            mCurrentPath.draw(mTranslucentDrawingCanvas);
        } else {
            mCurrentPath.drawLastPoint(mDrawingCanvas);
        }
        invalidate(updateRect);
    }

    public void addPath(int id, int strokeColor, float strokeWidth, ArrayList<PointF> points) {

        boolean exist = false;
        for (SketchData data : mPaths) {
            if (data.id == id) {
                exist = true;
                break;
            }
        }

        if (!exist) {
            SketchData newPath = new SketchData(id, strokeColor, strokeWidth, points);
            mPaths.add(newPath);
            boolean isErase = strokeColor == Color.TRANSPARENT;
            if (isErase && mDisableHardwareAccelerated == false) {
                mDisableHardwareAccelerated = true;
                setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            }
            newPath.draw(mDrawingCanvas);
            invalidateCanvas(true);
        }

    }

    public void deletePath(int id) {
        int index = -1;
        for (int i = 0; i < mPaths.size(); i++) {
            if (mPaths.get(i).id == id) {
                index = i;
                break;
            }
        }

        if (index > -1) {
            mPaths.remove(index);
            mNeedsFullRedraw = true;
            invalidateCanvas(true);
        }
    }

    public void end() {
        if (mCurrentPath != null) {
            if (mCurrentPath.isTranslucent) {
                mCurrentPath.draw(mDrawingCanvas);
                mTranslucentDrawingCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
            }
            mCurrentPath = null;
        }
    }

    public void onSaved(boolean success, String path) {
        WritableMap event = Arguments.createMap();
        event.putBoolean("success", success);
        event.putString("path", path);
        mContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                getId(),
                "topChange",
                event);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public void save(String format, String folder, String filename, boolean transparent, boolean includeImage,
            boolean cropToImageSize, boolean cropToBackgroundSize, boolean cropToForegroundSize) {
        File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + File.separator
                + folder);
        boolean success = f.exists() ? true : f.mkdirs();
        if (success) {

            Bitmap bitmap = createImage(format.equals("png") && transparent, includeImage, cropToImageSize,
                    cropToBackgroundSize, cropToForegroundSize);

            // send bitmap data to unity server
            sendBitmap(bitmap);

            boolean saveToDisk = false;
            if (saveToDisk) {
                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) +
                        File.separator + folder + File.separator + filename + (format.equals("png") ? ".png" : ".jpg"));
                try {
                    bitmap.compress(
                            format.equals("png") ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG,
                            format.equals("png") ? 100 : 90,
                            new FileOutputStream(file));
                    this.onSaved(true, file.getPath());
                } catch (Exception e) {
                    e.printStackTrace();
                    onSaved(false, null);
                }
            }

        } else {
            Log.e("SketchCanvas", "Failed to create folder!");
            onSaved(false, null);
        }
    }

    public static void sendBitmap(Bitmap bitmap) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Socket socket = null;
                OutputStream outputStream = null;
                DataOutputStream dataOutputStream = null;

                try {
                    // Establish a socket connection to the server
                    socket = new Socket("172.30.1.39", 12345); // Replace with your desired port number

                    // Convert the bitmap to bytes
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    byte[] bitmapBytes = stream.toByteArray();

                    // Send the length of the byte array to the server
                    dataOutputStream = new DataOutputStream(socket.getOutputStream());
                    dataOutputStream.writeInt(bitmapBytes.length);
                    dataOutputStream.flush();

                    // Send the bitmap data to the server
                    outputStream = socket.getOutputStream();
                    outputStream.write(bitmapBytes, 0, bitmapBytes.length);
                    outputStream.flush();

                    // Notify the server that all data has been sent
                    socket.shutdownOutput();

                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    // Close the connection and output stream
                    if (dataOutputStream != null) {
                        try {
                            dataOutputStream.close();
                            dataOutputStream = null;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    if (outputStream != null) {
                        try {
                            outputStream.close();
                            outputStream = null;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    if (socket != null) {
                        try {
                            socket.close();
                            socket = null;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

        }).start();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public String getBase64(String format, boolean transparent, boolean includeImage, boolean cropToImageSize,
            boolean cropToBackgroundSize, boolean cropToForegroundSize) {
        WritableMap event = Arguments.createMap();
        Bitmap bitmap = createImage(format.equals("png") && transparent, includeImage, cropToImageSize,
                cropToBackgroundSize, cropToForegroundSize);
        ByteArrayOutputStream byteArrayOS = new ByteArrayOutputStream();

        bitmap.compress(
                format.equals("png") ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG,
                format.equals("png") ? 100 : 90,
                byteArrayOS);
        return Base64.encodeToString(byteArrayOS.toByteArray(), Base64.DEFAULT);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (getWidth() > 0 && getHeight() > 0) {
            mDrawingBitmap = Bitmap.createBitmap(getWidth(), getHeight(),
                    Bitmap.Config.ARGB_8888);
            mDrawingCanvas = new Canvas(mDrawingBitmap);
            mTranslucentDrawingBitmap = Bitmap.createBitmap(getWidth(), getHeight(),
                    Bitmap.Config.ARGB_8888);
            mTranslucentDrawingCanvas = new Canvas(mTranslucentDrawingBitmap);

            mNeedsFullRedraw = true;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mBackgroundImage != null) {
            Rect dstRect = new Rect();
            canvas.getClipBounds(dstRect);
            canvas.drawBitmap(mBackgroundImage, null,
                    Utility.fillImage(mBackgroundImage.getWidth(), mBackgroundImage.getHeight(), dstRect.width(),
                            dstRect.height(), mContentMode),
                    null);
        }

        if (mNeedsFullRedraw && mDrawingCanvas != null) {
            mDrawingCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
            for (SketchData path : mPaths) {
                path.draw(mDrawingCanvas);
            }
            mNeedsFullRedraw = false;
        }

        if (mDrawingBitmap != null) {
            canvas.drawBitmap(mDrawingBitmap, 0, 0, mPaint);
        }

        if (mTranslucentDrawingBitmap != null && mCurrentPath != null && mCurrentPath.isTranslucent) {
            canvas.drawBitmap(mTranslucentDrawingBitmap, 0, 0, mPaint);
        }

        if (mForegroundImage != null) {
            Rect dstRect = new Rect();
            canvas.getClipBounds(dstRect);
            canvas.drawBitmap(mForegroundImage, null,
                    Utility.fillImage(mForegroundImage.getWidth(), mForegroundImage.getHeight(), dstRect.width(),
                            dstRect.height(), mContentMode),
                    null);

        }
    }

    private void invalidateCanvas(boolean shouldDispatchEvent) {
        if (shouldDispatchEvent) {
            WritableMap event = Arguments.createMap();
            event.putInt("pathsUpdate", mPaths.size());
            mContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                    getId(),
                    "topChange",
                    event);
        }
        invalidate();
    }

    private Bitmap createImage(boolean transparent, boolean includeImage, boolean cropToImageSize,
            boolean cropToBackgroundSize, boolean cropToForegroundSize) {

        Bitmap bitmap;

        // Create a bitmap with the specified width and height
        int width = cropToImageSize && mBackgroundImage != null ? mOriginalWidth : getWidth();
        int height = cropToImageSize && mBackgroundImage != null ? mOriginalHeight : getHeight();
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        canvas.drawARGB(transparent ? 0 : 255, 255, 255, 255);

        // 백그라운드 이미지와 마스크 이미지 설정은 동시에 하면 안됨...
        // 둘이 동시에 쓰일일은 없으니 다행 ㅎ

        // insert background image if available
        if (mBackgroundImage != null && includeImage) {
            Rect targetRect = new Rect();
            Utility.fillImage(mBackgroundImage.getWidth(), mBackgroundImage.getHeight(),
                    bitmap.getWidth(), bitmap.getHeight(), "AspectFit").roundOut(targetRect);
            canvas.drawBitmap(mBackgroundImage, null, targetRect, null);
        }

        // apply mask if available
        if (mMaskImage != null) {
            Rect targetRect = new Rect();

            Utility.fillImage(mMaskImage.getWidth(), mMaskImage.getHeight(),
                    bitmap.getWidth(), bitmap.getHeight(), "AspectFit").roundOut(targetRect);
            canvas.drawBitmap(mMaskImage, null, targetRect, mPaint);

            // set paint to mask mode
            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        }

        // draw strokes on top. lines are automatically masked due to mask mode
        if (mBackgroundImage != null && cropToImageSize) {
            Rect targetRect = new Rect();
            Utility.fillImage(mDrawingBitmap.getWidth(), mDrawingBitmap.getHeight(),
                    bitmap.getWidth(), bitmap.getHeight(), "AspectFill").roundOut(targetRect);
            canvas.drawBitmap(mDrawingBitmap, null, targetRect, mPaint);
        } else {
            canvas.drawBitmap(mDrawingBitmap, 0, 0, mPaint);
        }

        // reset paint
        mPaint = new Paint();

        // draw foreground image if available and include option is set
        if (mForegroundImage != null && includeImage) {
            Rect targetRect = new Rect();
            Utility.fillImage(mForegroundImage.getWidth(), mForegroundImage.getHeight(),
                    bitmap.getWidth(), bitmap.getHeight(), "AspectFit").roundOut(targetRect);
            canvas.drawBitmap(mForegroundImage, null, targetRect, null);
        }

        // crop bitmap to background or foreground size
        if (cropToBackgroundSize && mBackgroundImage != null) {
            Rect cropRect = new Rect();
            Utility.fillImage(mBackgroundImage.getWidth(), mBackgroundImage.getHeight(),
                    bitmap.getWidth(), bitmap.getHeight(), "AspectFit").roundOut(cropRect);

            Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top, cropRect.width(),
                    cropRect.height());

            return croppedBitmap;

        } else if (cropToForegroundSize && mForegroundImage != null) {
            Rect cropRect = new Rect();
            Utility.fillImage(mForegroundImage.getWidth(), mForegroundImage.getHeight(),
                    bitmap.getWidth(), bitmap.getHeight(), "AspectFit").roundOut(cropRect);

            Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top, cropRect.width(),
                    cropRect.height());

            return croppedBitmap;
        }

        return bitmap;
    }
}
