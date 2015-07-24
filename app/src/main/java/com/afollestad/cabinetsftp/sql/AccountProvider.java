package com.afollestad.cabinetsftp.sql;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.afollestad.cabinetsftp.api.SftpAccount;

/**
 * @author Aidan Follestad (afollestad)
 */
public class AccountProvider extends ProviderBase {

    public AccountProvider() {
        super("sftp_accounts", SftpAccount.COLUMNS);
    }

    private final static Uri URI = Uri.parse("content://com.afollestad.cabinetsftp.accounts");

//    public static List<SftpAccount> getAll(Context context) {
//        ContentResolver cr = context.getContentResolver();
//        Cursor cursor = cr.query(URI, null, null, null, null);
//        List<SftpAccount> accounts = new ArrayList<>();
//        if (cursor != null) {
//            while (cursor.moveToNext())
//                accounts.add(new SftpAccount(cursor));
//            cursor.close();
//        }
//        return accounts;
//    }

    public static boolean isEmpty(Context context) {
        ContentResolver cr = context.getContentResolver();
        Cursor cursor = cr.query(URI, null, null, null, null);
        if (cursor != null) {
            boolean empty = !(cursor.getCount() > 0 && cursor.moveToNext());
            cursor.close();
            return empty;
        }
        return false;
    }

    public static String add(Context context, SftpAccount account) {
        ContentResolver cr = context.getContentResolver();
        Uri uri = cr.insert(URI, account.getContentValues());
        return Long.parseLong(uri.getAuthority()) + "";
    }

    public static SftpAccount get(Context context, String accountId) {
        ContentResolver cr = context.getContentResolver();
        Cursor cursor = cr.query(URI, null, "_id = ?", new String[]{accountId}, null);
        if (cursor != null) {
            SftpAccount account = null;
            if (cursor.moveToFirst())
                account = new SftpAccount(cursor);
            cursor.close();
            return account;
        }
        return null;
    }

    public static boolean update(Context context, SftpAccount update, boolean addIfNotUpdated) {
        ContentResolver cr = context.getContentResolver();
        int updated = cr.update(URI, update.getContentValues(), "_id = ?", new String[]{update.id + ""});
        if (updated == 0 && addIfNotUpdated) {
            add(context, update);
            return true;
        } else return updated > 0;
    }

    public static boolean remove(Context context, String accountId) {
        ContentResolver cr = context.getContentResolver();
        return cr.delete(URI, "_id = ?", new String[]{accountId}) > 0;
    }

    public static void clear(Context context) {
        context.getContentResolver().delete(URI, null, null);
    }
}
