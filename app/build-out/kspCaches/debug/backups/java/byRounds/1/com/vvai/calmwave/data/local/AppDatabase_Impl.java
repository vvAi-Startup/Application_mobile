package com.vvai.calmwave.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile AnalyticsEventDao _analyticsEventDao;

  private volatile PendingAudioUploadDao _pendingAudioUploadDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `analytics_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` INTEGER, `eventType` TEXT NOT NULL, `detailsJson` TEXT NOT NULL, `screen` TEXT, `level` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `synced` INTEGER NOT NULL, `syncAttempts` INTEGER NOT NULL, `lastSyncAttempt` INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `pending_audio_uploads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `filePath` TEXT NOT NULL, `fileName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `deviceOrigin` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `synced` INTEGER NOT NULL, `syncAttempts` INTEGER NOT NULL, `lastSyncAttempt` INTEGER, `lastError` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'c6676cdcf7cf2c82fbd51c18e4a6a3d6')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `analytics_events`");
        db.execSQL("DROP TABLE IF EXISTS `pending_audio_uploads`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsAnalyticsEvents = new HashMap<String, TableInfo.Column>(10);
        _columnsAnalyticsEvents.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAnalyticsEvents.put("userId", new TableInfo.Column("userId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAnalyticsEvents.put("eventType", new TableInfo.Column("eventType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAnalyticsEvents.put("detailsJson", new TableInfo.Column("detailsJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAnalyticsEvents.put("screen", new TableInfo.Column("screen", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAnalyticsEvents.put("level", new TableInfo.Column("level", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAnalyticsEvents.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAnalyticsEvents.put("synced", new TableInfo.Column("synced", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAnalyticsEvents.put("syncAttempts", new TableInfo.Column("syncAttempts", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAnalyticsEvents.put("lastSyncAttempt", new TableInfo.Column("lastSyncAttempt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAnalyticsEvents = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesAnalyticsEvents = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoAnalyticsEvents = new TableInfo("analytics_events", _columnsAnalyticsEvents, _foreignKeysAnalyticsEvents, _indicesAnalyticsEvents);
        final TableInfo _existingAnalyticsEvents = TableInfo.read(db, "analytics_events");
        if (!_infoAnalyticsEvents.equals(_existingAnalyticsEvents)) {
          return new RoomOpenHelper.ValidationResult(false, "analytics_events(com.vvai.calmwave.data.model.AnalyticsEvent).\n"
                  + " Expected:\n" + _infoAnalyticsEvents + "\n"
                  + " Found:\n" + _existingAnalyticsEvents);
        }
        final HashMap<String, TableInfo.Column> _columnsPendingAudioUploads = new HashMap<String, TableInfo.Column>(10);
        _columnsPendingAudioUploads.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingAudioUploads.put("filePath", new TableInfo.Column("filePath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingAudioUploads.put("fileName", new TableInfo.Column("fileName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingAudioUploads.put("mimeType", new TableInfo.Column("mimeType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingAudioUploads.put("deviceOrigin", new TableInfo.Column("deviceOrigin", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingAudioUploads.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingAudioUploads.put("synced", new TableInfo.Column("synced", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingAudioUploads.put("syncAttempts", new TableInfo.Column("syncAttempts", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingAudioUploads.put("lastSyncAttempt", new TableInfo.Column("lastSyncAttempt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPendingAudioUploads.put("lastError", new TableInfo.Column("lastError", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPendingAudioUploads = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPendingAudioUploads = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPendingAudioUploads = new TableInfo("pending_audio_uploads", _columnsPendingAudioUploads, _foreignKeysPendingAudioUploads, _indicesPendingAudioUploads);
        final TableInfo _existingPendingAudioUploads = TableInfo.read(db, "pending_audio_uploads");
        if (!_infoPendingAudioUploads.equals(_existingPendingAudioUploads)) {
          return new RoomOpenHelper.ValidationResult(false, "pending_audio_uploads(com.vvai.calmwave.data.model.PendingAudioUpload).\n"
                  + " Expected:\n" + _infoPendingAudioUploads + "\n"
                  + " Found:\n" + _existingPendingAudioUploads);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "c6676cdcf7cf2c82fbd51c18e4a6a3d6", "b71148242dd1e7b2d865139f4e04927f");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "analytics_events","pending_audio_uploads");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `analytics_events`");
      _db.execSQL("DELETE FROM `pending_audio_uploads`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(AnalyticsEventDao.class, AnalyticsEventDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(PendingAudioUploadDao.class, PendingAudioUploadDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public AnalyticsEventDao analyticsEventDao() {
    if (_analyticsEventDao != null) {
      return _analyticsEventDao;
    } else {
      synchronized(this) {
        if(_analyticsEventDao == null) {
          _analyticsEventDao = new AnalyticsEventDao_Impl(this);
        }
        return _analyticsEventDao;
      }
    }
  }

  @Override
  public PendingAudioUploadDao pendingAudioUploadDao() {
    if (_pendingAudioUploadDao != null) {
      return _pendingAudioUploadDao;
    } else {
      synchronized(this) {
        if(_pendingAudioUploadDao == null) {
          _pendingAudioUploadDao = new PendingAudioUploadDao_Impl(this);
        }
        return _pendingAudioUploadDao;
      }
    }
  }
}
