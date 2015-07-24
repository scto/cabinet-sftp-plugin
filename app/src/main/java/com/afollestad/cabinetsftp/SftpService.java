package com.afollestad.cabinetsftp;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.afollestad.cabinet.plugins.PluginFile;
import com.afollestad.cabinet.plugins.PluginService;
import com.afollestad.cabinetsftp.api.SftpAccount;
import com.afollestad.cabinetsftp.sql.AccountProvider;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

/**
 * @author Aidan Follestad (afollestad)
 */
public class SftpService extends PluginService {

    private Session mSession;
    private ChannelSftp mChannel;
    private String mCurrentAccount;

    @Override
    protected boolean authenticationNeeded() {
        return AccountProvider.isEmpty(this);
    }

    @Override
    protected Intent authenticator() {
        return new Intent(this, AuthenticationActivity.class);
    }

    @Override
    protected Intent settings() {
        return new Intent(this, AuthenticationActivity.class);
    }

    @Override
    protected int getForegroundId() {
        return 6969;
    }

    @Override
    protected void connect() throws Exception {
        if (mCurrentAccount == null)
            throw new Exception("Current account is null.");
        final SftpAccount account = AccountProvider.get(this, mCurrentAccount);
        if (account == null)
            throw new Exception("Account not found: " + mCurrentAccount);

        if (mSession != null && mSession.isConnected())
            mSession.disconnect();
        if (mChannel != null && mChannel.isConnected())
            mChannel.disconnect();

        java.util.Properties config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no");
        JSch ssh = new JSch();

        try {
            final boolean useSshKey = account.sshKeyPath != null && !account.sshKeyPath.trim().isEmpty();
            if (useSshKey) {
                if (account.sshKeyPassword != null && !account.sshKeyPassword.trim().isEmpty()) {
                    ssh.addIdentity(account.sshKeyPath, account.sshKeyPassword);
                } else {
                    ssh.addIdentity(account.sshKeyPath);
                }
                config.put("PreferredAuthentications", "publickey");
            } else {
                config.put("PreferredAuthentications", "password");
            }

            //noinspection ConstantConditions
            mSession = ssh.getSession(account.username, account.host, account.port);
            mSession.setConfig(config);
            if (!useSshKey)
                mSession.setPassword(account.password);
            mSession.connect();
            mChannel = (ChannelSftp) mSession.openChannel("sftp");
            mChannel.connect();
        } catch (Exception e) {
            if (mSession != null) {
                mSession.disconnect();
                mSession = null;
            }
            if (mChannel != null) {
                mChannel.disconnect();
                mChannel = null;
            }
            throw e;
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    protected Uri openFile(PluginFile file) throws Exception {
        final File cacheDir = new File(getExternalCacheDir(), "OpenedFiles");
        cacheDir.mkdirs();
        final File cacheFile = new File(cacheDir, getFileName(file.getPath()));
        cacheFile.createNewFile();
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(cacheFile);
            mChannel.get(file.getPath(), os);
            return Uri.fromFile(cacheFile);
        } finally {
            closeQuietely(os);
        }
    }

    @Override
    protected PluginFile upload(Uri local, PluginFile dest) throws Exception {
        Log.v("SftpService", "Upload: " + local);
        InputStream is = null;
        try {
            is = openInputStream(local);
            mChannel.put(is, dest.getPath());
            SftpATTRS attrs = mChannel.lstat(dest.getPath());
            return fileFromAttrs(dest.getParent(), getFileName(dest.getPath()), attrs);
        } finally {
            closeQuietely(is);
        }
    }

    @Override
    protected Uri download(PluginFile remote, Uri local) throws Exception {
        OutputStream os = null;
        try {
            os = openOutputStream(local);
            mChannel.get(remote.getPath(), os);
            closeQuietely(os);
            return local;
        } finally {
            closeQuietely(os);
        }
    }

    private PluginFile fileFromAttrs(PluginFile parent, String name, SftpATTRS attrs) {
        final String path = parent.getPath();
        return new PluginFile.Builder(parent, this)
                .created(attrs.getMTime())
                .modified(attrs.getMTime())
                .isDir(attrs.isDir())
                .length(attrs.getSize())
                .path(path + (path.equals(File.separator) ? "" : File.separator) + name)
                .hidden(name.startsWith("."))
                .permissions(attrs.getPermissionsString())
                .build();
    }

    private void closeQuietely(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    protected List<PluginFile> listFiles(PluginFile parent) throws Exception {
        List<PluginFile> results = new ArrayList<>();
        final String path = parent.getPath();
        Vector vector = mChannel.ls(path);
        Enumeration enumer = vector.elements();
        while (enumer.hasMoreElements()) {
            ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) enumer.nextElement();
            if (entry.getFilename().equals(".") || entry.getFilename().equals("..")) continue;
            results.add(fileFromAttrs(parent, entry.getFilename(), entry.getAttrs()));
        }
        return results;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    protected PluginFile makeFile(String displayName, PluginFile parent) throws Exception {
        File temp = new File(getExternalCacheDir(), displayName);
        if (temp.exists())
            temp.delete();
        temp.createNewFile();
        String path = parent.getPath();
        if (!path.endsWith(File.separator)) path += File.separator;
        path += displayName;
        FileInputStream is = null;
        try {
            is = new FileInputStream(temp);
            mChannel.put(is, path);
            SftpATTRS attrs = mChannel.lstat(path);
            return fileFromAttrs(parent, displayName, attrs);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            closeQuietely(is);
        }
    }

    @Override
    protected PluginFile makeFolder(String displayName, PluginFile parent) throws Exception {
        final String path = parent.getPath() + File.separator + displayName;
        mChannel.mkdir(path);
        SftpATTRS attrs = mChannel.lstat(path);
        return fileFromAttrs(parent, displayName, attrs);
    }

    @Override
    protected PluginFile copy(PluginFile source, PluginFile dest) throws Exception {
        if (source.isDir()) {
            return makeFolder(getFileName(dest.getPath()), dest.getParent());
        } else {
            FileOutputStream os = null;
            FileInputStream is = null;
            File cache = new File(getExternalCacheDir(), source.getPath().replace(File.separator, "_"));
            try {
                os = new FileOutputStream(cache);
                mChannel.get(source.getPath(), os);
                os.close();
                is = new FileInputStream(cache);
                mChannel.put(is, dest.getPath());
                SftpATTRS attrs = mChannel.lstat(dest.getPath());
                return fileFromAttrs(dest.getParent(), getFileName(dest.getPath()), attrs);
            } finally {
                closeQuietely(os);
                closeQuietely(is);
                //noinspection ResultOfMethodCallIgnored
                cache.delete();
            }
        }
    }

    @Override
    protected boolean remove(PluginFile file) throws Exception {
        if (file.isDir()) {
            ChannelExec exec = (ChannelExec) mSession.openChannel("exec");
            try {
                exec.setCommand("rm -rf \"" + file.getPath() + "\"");
                exec.connect();
            } finally {
                if (exec.isConnected())
                    exec.disconnect();
            }
        } else {
            mChannel.rm(file.getPath());
        }
        return true;
    }

    @Override
    protected boolean exists(String path) throws Exception {
        try {
            mChannel.lstat(path);
            return true;
        } catch (Exception e) {
            if (e instanceof SftpException) {
                SftpException sftpError = (SftpException) e;
                if (sftpError.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    return false;
                } else throw e;
            } else throw e;
        }
    }

    @Override
    protected void chmod(int permissions, PluginFile target) throws Exception {
        mChannel.chmod(Integer.parseInt(permissions + "", 8), target.getPath());
    }

    @Override
    protected void chown(int uid, PluginFile target) throws Exception {
        mChannel.chown(uid, target.getPath());
    }

    @Override
    protected void disconnect() throws Exception {
        if (mSession != null)
            mSession.disconnect();
        if (mChannel != null)
            mChannel.disconnect();
        mSession = null;
        mChannel = null;
    }

    @Override
    protected boolean isConnected() {
        return mChannel != null && mChannel.isConnected();
    }

    @Override
    protected void setCurrentAccount(String accountId) {
        mCurrentAccount = accountId;
    }

    @Override
    protected String getCurrentAccount() {
        return mCurrentAccount;
    }

    @Override
    protected void removeAccount(String accountId) throws Exception {
        AccountProvider.remove(this, accountId);
    }
}