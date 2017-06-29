package com.ironz.binaryprefs.file.adapter;

import com.ironz.binaryprefs.exception.FileOperationException;
import com.ironz.binaryprefs.file.directory.DirectoryProvider;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * File adapter implementation which performs NIO file operations.
 * This implementation support IPC locks and makes back-up for
 * each file before new data will be written. See {@link #backupAndSave(String, byte[])}.
 * After success write backup file will be removed.
 * If adapter detects backup file it will be replaced
 * to original file. See {@link #fetchBackupOrOriginal(String)}.
 */
public final class NioFileAdapter implements FileAdapter {

    private static final String[] EMPTY_STRING_NAMES_ARRAY = new String[0];

    private static final String BACKUP_EXTENSION = ".bak";

    private static final String R_MODE = "r";
    private static final String RWD_MODE = "rwd";

    private final File baseDir;
    private final File backupDir;

    public NioFileAdapter(DirectoryProvider directoryProvider) {
        this.baseDir = directoryProvider.getStoreDirectory();
        this.backupDir = directoryProvider.getBackupDirectory();
    }

    @Override
    public String[] names() {
        return namesInternal();
    }

    private String[] namesInternal() {
        String[] list = baseDir.list();
        if (list == null) {
            return EMPTY_STRING_NAMES_ARRAY;
        }
        return list;
    }

    @Override
    public byte[] fetch(String name) {
        return fetchBackupOrOriginal(name);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private byte[] fetchBackupOrOriginal(String name) {
        File backupFile = new File(backupDir, name + BACKUP_EXTENSION);
        File file = new File(baseDir, name);
        if (backupFile.exists()) {
            deleteOriginal(file);
            swap(backupFile, file);
        }
        return fetchInternal(file);
    }

    private void deleteOriginal(File file) {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private byte[] fetchInternal(File file) {
        FileChannel channel = null;
        RandomAccessFile randomAccessFile = null;
        try {
            randomAccessFile = new RandomAccessFile(file, R_MODE);
            channel = randomAccessFile.getChannel();
            int size = (int) randomAccessFile.length();
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, size);
            byte[] bytes = new byte[size];
            buffer.get(bytes);
            return bytes;
        } catch (Exception e) {
            throw new FileOperationException(e);
        } finally {
            try {
                if (randomAccessFile != null) {
                    randomAccessFile.close();
                }
                if (channel != null) {
                    channel.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void save(String name, byte[] bytes) {
        backupAndSave(name, bytes);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void backupAndSave(String name, byte[] bytes) {
        File file = new File(baseDir, name);
        File backupFile = new File(backupDir, name + BACKUP_EXTENSION);
        swap(file, backupFile);
        saveInternal(file, bytes);
        deleteBackup(backupFile);
    }

    private void deleteBackup(File backupFile) {
        //noinspection ResultOfMethodCallIgnored
        backupFile.delete();
    }

    private void swap(File from, File to) {
        //noinspection ResultOfMethodCallIgnored
        from.renameTo(to);
    }

    private void saveInternal(File file, byte[] bytes) {
        FileChannel channel = null;
        RandomAccessFile randomAccessFile = null;
        try {
            randomAccessFile = new RandomAccessFile(file, RWD_MODE);
            randomAccessFile.setLength(0);
            channel = randomAccessFile.getChannel();
            MappedByteBuffer byteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, bytes.length);
            byteBuffer.put(bytes);
            channel.write(byteBuffer);
            byteBuffer.force();
        } catch (Exception e) {
            throw new FileOperationException(e);
        } finally {
            try {
                if (randomAccessFile != null) {
                    randomAccessFile.close();
                }
                if (channel != null) {
                    channel.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void remove(String name) {
        removeInternal(name);
    }

    private void removeInternal(String name) {
        try {
            File file = new File(baseDir, name);
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        } catch (Exception e) {
            throw new FileOperationException(e);
        }
    }
}