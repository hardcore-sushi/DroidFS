package sushi.hardcore.droidfs.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.text.DecimalFormat;

public class PathUtils {

    public static String getParentPath(String path){
        if (path.endsWith("/")){
            String a = path.substring(0, path.length()-2);
            if (a.contains("/")){
                return a.substring(0, a.lastIndexOf("/"));
            } else {
                return "";
            }
        } else {
            if (path.contains("/")){
                return path.substring(0, path.lastIndexOf("/"));
            } else {
                return "";
            }
        }
    }

    public static String path_join(String... strings){
        StringBuilder result = new StringBuilder();
        for (String element : strings){
            if (!element.isEmpty()){
                if (!element.endsWith("/")){
                    element += "/";
                }
                result.append(element);
            }
        }
        return result.substring(0, result.length()-1);
    }

    public static String getRelativePath(String parentPath, String childPath){
        return childPath.substring(parentPath.length()+1);
    }

    public static String getFilenameFromURI(Context context, Uri uri){
        String result = null;
        if (uri.getScheme().equals("content")){
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null){
                try {
                    if (cursor.moveToFirst()){
                        result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    }
                } finally {
                    cursor.close();
                }
            }
        }
        if (result == null){
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1){
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    static final String[] units = new String[]{"B", "kB", "MB", "GB", "TB"};
    public static String formatSize(long size){
        if (size <= 0){
            return "0 B";
        }
        int digitGroups = (int)(Math.log10(size)/Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size/Math.pow(1024, digitGroups))+" "+units[digitGroups];
    }

    public static Boolean isTreeUriOnPrimaryStorage(Uri treeUri){
        String volumeId = getVolumeIdFromTreeUri(treeUri);
        if (volumeId != null) {
            return volumeId.equals(PRIMARY_VOLUME_NAME) || volumeId.equals("home") || volumeId.equals("downloads");
        } else {
            return false;
        }
    }

    private static final String PRIMARY_VOLUME_NAME = "primary";
    @Nullable
    public static String getFullPathFromTreeUri(@Nullable Uri treeUri, Context context) {
        if (treeUri == null) return null;
        if ("content".equalsIgnoreCase(treeUri.getScheme())) {
            String volumePath = getVolumePath(getVolumeIdFromTreeUri(treeUri),context);
            if (volumePath == null) return null;
            if (volumePath.endsWith(File.separator))
                volumePath = volumePath.substring(0, volumePath.length() - 1);
            String documentPath = getDocumentPathFromTreeUri(treeUri);
            if (documentPath.endsWith(File.separator))
                documentPath = documentPath.substring(0, documentPath.length() - 1);
            if (documentPath.length() > 0) {
                return path_join(volumePath, documentPath);
            }
            else return volumePath;
        } else if ("file".equalsIgnoreCase(treeUri.getScheme())) {
            return treeUri.getPath();
        }
        return null;
    }

    private static String getVolumePath(final String volumeId, Context context) {
        try {
            StorageManager mStorageManager =
                    (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method getUuid = storageVolumeClazz.getMethod("getUuid");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isPrimary = storageVolumeClazz.getMethod("isPrimary");
            Object result = getVolumeList.invoke(mStorageManager);

            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                String uuid = (String) getUuid.invoke(storageVolumeElement);
                Boolean primary = (Boolean) isPrimary.invoke(storageVolumeElement);
                if (primary && PRIMARY_VOLUME_NAME.equals(volumeId))
                    return (String) getPath.invoke(storageVolumeElement);
                if (uuid != null && uuid.equals(volumeId))
                    return (String) getPath.invoke(storageVolumeElement);
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String getVolumeIdFromTreeUri(final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if (split.length > 0) return split[0];
        else return null;
    }

    private static String getDocumentPathFromTreeUri(final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if ((split.length >= 2) && (split[1] != null)) return split[1];
        else return File.separator;
    }
}
