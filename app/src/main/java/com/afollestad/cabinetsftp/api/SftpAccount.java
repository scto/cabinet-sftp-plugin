package com.afollestad.cabinetsftp.api;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;

/**
 * @author Aidan Follestad (afollestad)
 */
public final class SftpAccount {

    public final static String COLUMNS = "_id INTEGER PRIMARY KEY AUTOINCREMENT, host TEXT, port INTEGER, user TEXT, pass TEXT, key_path TEXT, key_pass TEXT, initial_path TEXT";

    public int id;
    public String host;
    public int port;
    public String username;
    public String password;
    public String sshKeyPath;
    public String sshKeyPassword;
    public String initialPath;

    public SftpAccount() {
    }

    public SftpAccount(Cursor cursor) {
        id = cursor.getInt(0);
        host = cursor.getString(1);
        port = cursor.getInt(2);
        username = cursor.getString(3);
        password = cursor.getString(4);
        sshKeyPath = cursor.getString(5);
        sshKeyPassword = cursor.getString(6);
        initialPath = cursor.getString(7);
    }

    private void addToQuery(StringBuilder query, String name, Object val) {
        if (query.length() > 0)
            query.append(" AND ");
        query.append(name);
        if (val == null) {
            query.append(" IS NULL");
        } else {
            query.append(" = ");
            if (val instanceof Integer)
                query.append(val);
            else if (val instanceof Boolean)
                query.append(((Boolean) val) ? 1 : 0);
            else
                query.append(DatabaseUtils.sqlEscapeString((String) val));
        }
    }

    public String where() {
        StringBuilder where = new StringBuilder();
        addToQuery(where, "_id", id);
        return where.toString();
    }

    public ContentValues getContentValues() {
        ContentValues values = new ContentValues(7);
        values.put("host", host);
        values.put("port", port);
        values.put("user", username);
        values.put("pass", password);
        values.put("key_path", sshKeyPath);
        values.put("key_pass", sshKeyPassword);
        values.put("initial_path", initialPath);
        return values;
    }
}
