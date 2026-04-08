package com.vvai.calmwave.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.vvai.calmwave.data.model.AnalyticsEvent;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AnalyticsEventDao_Impl implements AnalyticsEventDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<AnalyticsEvent> __insertionAdapterOfAnalyticsEvent;

  private final EntityDeletionOrUpdateAdapter<AnalyticsEvent> __updateAdapterOfAnalyticsEvent;

  private final SharedSQLiteStatement __preparedStmtOfMarkAsSynced;

  private final SharedSQLiteStatement __preparedStmtOfIncrementSyncAttempts;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSyncedEventsBefore;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllSyncedEvents;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  public AnalyticsEventDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfAnalyticsEvent = new EntityInsertionAdapter<AnalyticsEvent>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `analytics_events` (`id`,`userId`,`eventType`,`detailsJson`,`screen`,`level`,`timestamp`,`synced`,`syncAttempts`,`lastSyncAttempt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AnalyticsEvent entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getUserId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindLong(2, entity.getUserId());
        }
        statement.bindString(3, entity.getEventType());
        statement.bindString(4, entity.getDetailsJson());
        if (entity.getScreen() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getScreen());
        }
        statement.bindString(6, entity.getLevel());
        statement.bindLong(7, entity.getTimestamp());
        final int _tmp = entity.getSynced() ? 1 : 0;
        statement.bindLong(8, _tmp);
        statement.bindLong(9, entity.getSyncAttempts());
        if (entity.getLastSyncAttempt() == null) {
          statement.bindNull(10);
        } else {
          statement.bindLong(10, entity.getLastSyncAttempt());
        }
      }
    };
    this.__updateAdapterOfAnalyticsEvent = new EntityDeletionOrUpdateAdapter<AnalyticsEvent>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `analytics_events` SET `id` = ?,`userId` = ?,`eventType` = ?,`detailsJson` = ?,`screen` = ?,`level` = ?,`timestamp` = ?,`synced` = ?,`syncAttempts` = ?,`lastSyncAttempt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AnalyticsEvent entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getUserId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindLong(2, entity.getUserId());
        }
        statement.bindString(3, entity.getEventType());
        statement.bindString(4, entity.getDetailsJson());
        if (entity.getScreen() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getScreen());
        }
        statement.bindString(6, entity.getLevel());
        statement.bindLong(7, entity.getTimestamp());
        final int _tmp = entity.getSynced() ? 1 : 0;
        statement.bindLong(8, _tmp);
        statement.bindLong(9, entity.getSyncAttempts());
        if (entity.getLastSyncAttempt() == null) {
          statement.bindNull(10);
        } else {
          statement.bindLong(10, entity.getLastSyncAttempt());
        }
        statement.bindLong(11, entity.getId());
      }
    };
    this.__preparedStmtOfMarkAsSynced = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE analytics_events SET synced = 1 WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfIncrementSyncAttempts = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE analytics_events SET syncAttempts = syncAttempts + 1, lastSyncAttempt = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteSyncedEventsBefore = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM analytics_events WHERE synced = 1 AND timestamp < ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllSyncedEvents = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM analytics_events WHERE synced = 1";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM analytics_events WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final AnalyticsEvent event, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfAnalyticsEvent.insertAndReturnId(event);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<AnalyticsEvent> events,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfAnalyticsEvent.insert(events);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final AnalyticsEvent event, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfAnalyticsEvent.handle(event);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object markAsSynced(final long eventId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkAsSynced.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, eventId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfMarkAsSynced.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object incrementSyncAttempts(final long eventId, final long timestamp,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfIncrementSyncAttempts.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, timestamp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, eventId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfIncrementSyncAttempts.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteSyncedEventsBefore(final long timestampBefore,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSyncedEventsBefore.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, timestampBefore);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteSyncedEventsBefore.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllSyncedEvents(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllSyncedEvents.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAllSyncedEvents.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final long eventId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, eventId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getUnsyncedEvents(final Continuation<? super List<AnalyticsEvent>> $completion) {
    final String _sql = "SELECT * FROM analytics_events WHERE synced = 0 ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<AnalyticsEvent>>() {
      @Override
      @NonNull
      public List<AnalyticsEvent> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfEventType = CursorUtil.getColumnIndexOrThrow(_cursor, "eventType");
          final int _cursorIndexOfDetailsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "detailsJson");
          final int _cursorIndexOfScreen = CursorUtil.getColumnIndexOrThrow(_cursor, "screen");
          final int _cursorIndexOfLevel = CursorUtil.getColumnIndexOrThrow(_cursor, "level");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
          final int _cursorIndexOfSyncAttempts = CursorUtil.getColumnIndexOrThrow(_cursor, "syncAttempts");
          final int _cursorIndexOfLastSyncAttempt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSyncAttempt");
          final List<AnalyticsEvent> _result = new ArrayList<AnalyticsEvent>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AnalyticsEvent _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final Long _tmpUserId;
            if (_cursor.isNull(_cursorIndexOfUserId)) {
              _tmpUserId = null;
            } else {
              _tmpUserId = _cursor.getLong(_cursorIndexOfUserId);
            }
            final String _tmpEventType;
            _tmpEventType = _cursor.getString(_cursorIndexOfEventType);
            final String _tmpDetailsJson;
            _tmpDetailsJson = _cursor.getString(_cursorIndexOfDetailsJson);
            final String _tmpScreen;
            if (_cursor.isNull(_cursorIndexOfScreen)) {
              _tmpScreen = null;
            } else {
              _tmpScreen = _cursor.getString(_cursorIndexOfScreen);
            }
            final String _tmpLevel;
            _tmpLevel = _cursor.getString(_cursorIndexOfLevel);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSynced);
            _tmpSynced = _tmp != 0;
            final int _tmpSyncAttempts;
            _tmpSyncAttempts = _cursor.getInt(_cursorIndexOfSyncAttempts);
            final Long _tmpLastSyncAttempt;
            if (_cursor.isNull(_cursorIndexOfLastSyncAttempt)) {
              _tmpLastSyncAttempt = null;
            } else {
              _tmpLastSyncAttempt = _cursor.getLong(_cursorIndexOfLastSyncAttempt);
            }
            _item = new AnalyticsEvent(_tmpId,_tmpUserId,_tmpEventType,_tmpDetailsJson,_tmpScreen,_tmpLevel,_tmpTimestamp,_tmpSynced,_tmpSyncAttempts,_tmpLastSyncAttempt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getUnsyncedEventsLimited(final int limit,
      final Continuation<? super List<AnalyticsEvent>> $completion) {
    final String _sql = "SELECT * FROM analytics_events WHERE synced = 0 ORDER BY timestamp ASC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<AnalyticsEvent>>() {
      @Override
      @NonNull
      public List<AnalyticsEvent> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfEventType = CursorUtil.getColumnIndexOrThrow(_cursor, "eventType");
          final int _cursorIndexOfDetailsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "detailsJson");
          final int _cursorIndexOfScreen = CursorUtil.getColumnIndexOrThrow(_cursor, "screen");
          final int _cursorIndexOfLevel = CursorUtil.getColumnIndexOrThrow(_cursor, "level");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
          final int _cursorIndexOfSyncAttempts = CursorUtil.getColumnIndexOrThrow(_cursor, "syncAttempts");
          final int _cursorIndexOfLastSyncAttempt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSyncAttempt");
          final List<AnalyticsEvent> _result = new ArrayList<AnalyticsEvent>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AnalyticsEvent _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final Long _tmpUserId;
            if (_cursor.isNull(_cursorIndexOfUserId)) {
              _tmpUserId = null;
            } else {
              _tmpUserId = _cursor.getLong(_cursorIndexOfUserId);
            }
            final String _tmpEventType;
            _tmpEventType = _cursor.getString(_cursorIndexOfEventType);
            final String _tmpDetailsJson;
            _tmpDetailsJson = _cursor.getString(_cursorIndexOfDetailsJson);
            final String _tmpScreen;
            if (_cursor.isNull(_cursorIndexOfScreen)) {
              _tmpScreen = null;
            } else {
              _tmpScreen = _cursor.getString(_cursorIndexOfScreen);
            }
            final String _tmpLevel;
            _tmpLevel = _cursor.getString(_cursorIndexOfLevel);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSynced);
            _tmpSynced = _tmp != 0;
            final int _tmpSyncAttempts;
            _tmpSyncAttempts = _cursor.getInt(_cursorIndexOfSyncAttempts);
            final Long _tmpLastSyncAttempt;
            if (_cursor.isNull(_cursorIndexOfLastSyncAttempt)) {
              _tmpLastSyncAttempt = null;
            } else {
              _tmpLastSyncAttempt = _cursor.getLong(_cursorIndexOfLastSyncAttempt);
            }
            _item = new AnalyticsEvent(_tmpId,_tmpUserId,_tmpEventType,_tmpDetailsJson,_tmpScreen,_tmpLevel,_tmpTimestamp,_tmpSynced,_tmpSyncAttempts,_tmpLastSyncAttempt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object countUnsyncedEvents(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM analytics_events WHERE synced = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<AnalyticsEvent>> observeUnsyncedEvents() {
    final String _sql = "SELECT * FROM analytics_events WHERE synced = 0 ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"analytics_events"}, new Callable<List<AnalyticsEvent>>() {
      @Override
      @NonNull
      public List<AnalyticsEvent> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfEventType = CursorUtil.getColumnIndexOrThrow(_cursor, "eventType");
          final int _cursorIndexOfDetailsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "detailsJson");
          final int _cursorIndexOfScreen = CursorUtil.getColumnIndexOrThrow(_cursor, "screen");
          final int _cursorIndexOfLevel = CursorUtil.getColumnIndexOrThrow(_cursor, "level");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
          final int _cursorIndexOfSyncAttempts = CursorUtil.getColumnIndexOrThrow(_cursor, "syncAttempts");
          final int _cursorIndexOfLastSyncAttempt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSyncAttempt");
          final List<AnalyticsEvent> _result = new ArrayList<AnalyticsEvent>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AnalyticsEvent _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final Long _tmpUserId;
            if (_cursor.isNull(_cursorIndexOfUserId)) {
              _tmpUserId = null;
            } else {
              _tmpUserId = _cursor.getLong(_cursorIndexOfUserId);
            }
            final String _tmpEventType;
            _tmpEventType = _cursor.getString(_cursorIndexOfEventType);
            final String _tmpDetailsJson;
            _tmpDetailsJson = _cursor.getString(_cursorIndexOfDetailsJson);
            final String _tmpScreen;
            if (_cursor.isNull(_cursorIndexOfScreen)) {
              _tmpScreen = null;
            } else {
              _tmpScreen = _cursor.getString(_cursorIndexOfScreen);
            }
            final String _tmpLevel;
            _tmpLevel = _cursor.getString(_cursorIndexOfLevel);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSynced);
            _tmpSynced = _tmp != 0;
            final int _tmpSyncAttempts;
            _tmpSyncAttempts = _cursor.getInt(_cursorIndexOfSyncAttempts);
            final Long _tmpLastSyncAttempt;
            if (_cursor.isNull(_cursorIndexOfLastSyncAttempt)) {
              _tmpLastSyncAttempt = null;
            } else {
              _tmpLastSyncAttempt = _cursor.getLong(_cursorIndexOfLastSyncAttempt);
            }
            _item = new AnalyticsEvent(_tmpId,_tmpUserId,_tmpEventType,_tmpDetailsJson,_tmpScreen,_tmpLevel,_tmpTimestamp,_tmpSynced,_tmpSyncAttempts,_tmpLastSyncAttempt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getAllEvents(final Continuation<? super List<AnalyticsEvent>> $completion) {
    final String _sql = "SELECT * FROM analytics_events ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<AnalyticsEvent>>() {
      @Override
      @NonNull
      public List<AnalyticsEvent> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfEventType = CursorUtil.getColumnIndexOrThrow(_cursor, "eventType");
          final int _cursorIndexOfDetailsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "detailsJson");
          final int _cursorIndexOfScreen = CursorUtil.getColumnIndexOrThrow(_cursor, "screen");
          final int _cursorIndexOfLevel = CursorUtil.getColumnIndexOrThrow(_cursor, "level");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
          final int _cursorIndexOfSyncAttempts = CursorUtil.getColumnIndexOrThrow(_cursor, "syncAttempts");
          final int _cursorIndexOfLastSyncAttempt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastSyncAttempt");
          final List<AnalyticsEvent> _result = new ArrayList<AnalyticsEvent>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AnalyticsEvent _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final Long _tmpUserId;
            if (_cursor.isNull(_cursorIndexOfUserId)) {
              _tmpUserId = null;
            } else {
              _tmpUserId = _cursor.getLong(_cursorIndexOfUserId);
            }
            final String _tmpEventType;
            _tmpEventType = _cursor.getString(_cursorIndexOfEventType);
            final String _tmpDetailsJson;
            _tmpDetailsJson = _cursor.getString(_cursorIndexOfDetailsJson);
            final String _tmpScreen;
            if (_cursor.isNull(_cursorIndexOfScreen)) {
              _tmpScreen = null;
            } else {
              _tmpScreen = _cursor.getString(_cursorIndexOfScreen);
            }
            final String _tmpLevel;
            _tmpLevel = _cursor.getString(_cursorIndexOfLevel);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSynced);
            _tmpSynced = _tmp != 0;
            final int _tmpSyncAttempts;
            _tmpSyncAttempts = _cursor.getInt(_cursorIndexOfSyncAttempts);
            final Long _tmpLastSyncAttempt;
            if (_cursor.isNull(_cursorIndexOfLastSyncAttempt)) {
              _tmpLastSyncAttempt = null;
            } else {
              _tmpLastSyncAttempt = _cursor.getLong(_cursorIndexOfLastSyncAttempt);
            }
            _item = new AnalyticsEvent(_tmpId,_tmpUserId,_tmpEventType,_tmpDetailsJson,_tmpScreen,_tmpLevel,_tmpTimestamp,_tmpSynced,_tmpSyncAttempts,_tmpLastSyncAttempt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object markMultipleAsSynced(final List<Long> eventIds,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("UPDATE analytics_events SET synced = 1 WHERE id IN (");
        final int _inputSize = eventIds.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        for (long _item : eventIds) {
          _stmt.bindLong(_argIndex, _item);
          _argIndex++;
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
