package com.safepulse.data.db;

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
import com.safepulse.data.db.dao.EmergencyContactDao;
import com.safepulse.data.db.dao.EmergencyContactDao_Impl;
import com.safepulse.data.db.dao.EmergencyServiceDao;
import com.safepulse.data.db.dao.EmergencyServiceDao_Impl;
import com.safepulse.data.db.dao.EventLogDao;
import com.safepulse.data.db.dao.EventLogDao_Impl;
import com.safepulse.data.db.dao.HotspotDao;
import com.safepulse.data.db.dao.HotspotDao_Impl;
import com.safepulse.data.db.dao.UnsafeZoneDao;
import com.safepulse.data.db.dao.UnsafeZoneDao_Impl;
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
public final class SafePulseDatabase_Impl extends SafePulseDatabase {
  private volatile HotspotDao _hotspotDao;

  private volatile UnsafeZoneDao _unsafeZoneDao;

  private volatile EmergencyContactDao _emergencyContactDao;

  private volatile EventLogDao _eventLogDao;

  private volatile EmergencyServiceDao _emergencyServiceDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `hotspots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `radiusMeters` REAL NOT NULL, `baseRisk` REAL NOT NULL, `timeBucket` TEXT NOT NULL, `roadType` TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `unsafe_zones` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `radiusMeters` REAL NOT NULL, `crimeScore` REAL NOT NULL, `lightingScore` REAL NOT NULL, `footfallScore` REAL NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `emergency_contacts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, `isPrimary` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `event_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `type` TEXT NOT NULL, `confidence` REAL NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `mode` TEXT NOT NULL, `wasSOSSent` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `emergency_services` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `address` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `city` TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '1ac94848b9da8f5eb615ff63566259b9')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `hotspots`");
        db.execSQL("DROP TABLE IF EXISTS `unsafe_zones`");
        db.execSQL("DROP TABLE IF EXISTS `emergency_contacts`");
        db.execSQL("DROP TABLE IF EXISTS `event_logs`");
        db.execSQL("DROP TABLE IF EXISTS `emergency_services`");
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
        final HashMap<String, TableInfo.Column> _columnsHotspots = new HashMap<String, TableInfo.Column>(7);
        _columnsHotspots.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHotspots.put("lat", new TableInfo.Column("lat", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHotspots.put("lng", new TableInfo.Column("lng", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHotspots.put("radiusMeters", new TableInfo.Column("radiusMeters", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHotspots.put("baseRisk", new TableInfo.Column("baseRisk", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHotspots.put("timeBucket", new TableInfo.Column("timeBucket", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHotspots.put("roadType", new TableInfo.Column("roadType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysHotspots = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesHotspots = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoHotspots = new TableInfo("hotspots", _columnsHotspots, _foreignKeysHotspots, _indicesHotspots);
        final TableInfo _existingHotspots = TableInfo.read(db, "hotspots");
        if (!_infoHotspots.equals(_existingHotspots)) {
          return new RoomOpenHelper.ValidationResult(false, "hotspots(com.safepulse.data.db.entity.HotspotEntity).\n"
                  + " Expected:\n" + _infoHotspots + "\n"
                  + " Found:\n" + _existingHotspots);
        }
        final HashMap<String, TableInfo.Column> _columnsUnsafeZones = new HashMap<String, TableInfo.Column>(7);
        _columnsUnsafeZones.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUnsafeZones.put("lat", new TableInfo.Column("lat", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUnsafeZones.put("lng", new TableInfo.Column("lng", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUnsafeZones.put("radiusMeters", new TableInfo.Column("radiusMeters", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUnsafeZones.put("crimeScore", new TableInfo.Column("crimeScore", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUnsafeZones.put("lightingScore", new TableInfo.Column("lightingScore", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUnsafeZones.put("footfallScore", new TableInfo.Column("footfallScore", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysUnsafeZones = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesUnsafeZones = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoUnsafeZones = new TableInfo("unsafe_zones", _columnsUnsafeZones, _foreignKeysUnsafeZones, _indicesUnsafeZones);
        final TableInfo _existingUnsafeZones = TableInfo.read(db, "unsafe_zones");
        if (!_infoUnsafeZones.equals(_existingUnsafeZones)) {
          return new RoomOpenHelper.ValidationResult(false, "unsafe_zones(com.safepulse.data.db.entity.UnsafeZoneEntity).\n"
                  + " Expected:\n" + _infoUnsafeZones + "\n"
                  + " Found:\n" + _existingUnsafeZones);
        }
        final HashMap<String, TableInfo.Column> _columnsEmergencyContacts = new HashMap<String, TableInfo.Column>(4);
        _columnsEmergencyContacts.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEmergencyContacts.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEmergencyContacts.put("phone", new TableInfo.Column("phone", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEmergencyContacts.put("isPrimary", new TableInfo.Column("isPrimary", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysEmergencyContacts = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesEmergencyContacts = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoEmergencyContacts = new TableInfo("emergency_contacts", _columnsEmergencyContacts, _foreignKeysEmergencyContacts, _indicesEmergencyContacts);
        final TableInfo _existingEmergencyContacts = TableInfo.read(db, "emergency_contacts");
        if (!_infoEmergencyContacts.equals(_existingEmergencyContacts)) {
          return new RoomOpenHelper.ValidationResult(false, "emergency_contacts(com.safepulse.data.db.entity.EmergencyContactEntity).\n"
                  + " Expected:\n" + _infoEmergencyContacts + "\n"
                  + " Found:\n" + _existingEmergencyContacts);
        }
        final HashMap<String, TableInfo.Column> _columnsEventLogs = new HashMap<String, TableInfo.Column>(8);
        _columnsEventLogs.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEventLogs.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEventLogs.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEventLogs.put("confidence", new TableInfo.Column("confidence", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEventLogs.put("lat", new TableInfo.Column("lat", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEventLogs.put("lng", new TableInfo.Column("lng", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEventLogs.put("mode", new TableInfo.Column("mode", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEventLogs.put("wasSOSSent", new TableInfo.Column("wasSOSSent", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysEventLogs = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesEventLogs = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoEventLogs = new TableInfo("event_logs", _columnsEventLogs, _foreignKeysEventLogs, _indicesEventLogs);
        final TableInfo _existingEventLogs = TableInfo.read(db, "event_logs");
        if (!_infoEventLogs.equals(_existingEventLogs)) {
          return new RoomOpenHelper.ValidationResult(false, "event_logs(com.safepulse.data.db.entity.EventLogEntity).\n"
                  + " Expected:\n" + _infoEventLogs + "\n"
                  + " Found:\n" + _existingEventLogs);
        }
        final HashMap<String, TableInfo.Column> _columnsEmergencyServices = new HashMap<String, TableInfo.Column>(8);
        _columnsEmergencyServices.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEmergencyServices.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEmergencyServices.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEmergencyServices.put("address", new TableInfo.Column("address", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEmergencyServices.put("phoneNumber", new TableInfo.Column("phoneNumber", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEmergencyServices.put("lat", new TableInfo.Column("lat", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEmergencyServices.put("lng", new TableInfo.Column("lng", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEmergencyServices.put("city", new TableInfo.Column("city", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysEmergencyServices = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesEmergencyServices = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoEmergencyServices = new TableInfo("emergency_services", _columnsEmergencyServices, _foreignKeysEmergencyServices, _indicesEmergencyServices);
        final TableInfo _existingEmergencyServices = TableInfo.read(db, "emergency_services");
        if (!_infoEmergencyServices.equals(_existingEmergencyServices)) {
          return new RoomOpenHelper.ValidationResult(false, "emergency_services(com.safepulse.data.db.entity.EmergencyServiceEntity).\n"
                  + " Expected:\n" + _infoEmergencyServices + "\n"
                  + " Found:\n" + _existingEmergencyServices);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "1ac94848b9da8f5eb615ff63566259b9", "4343e722e2a60cf18749210b3c566af1");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "hotspots","unsafe_zones","emergency_contacts","event_logs","emergency_services");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `hotspots`");
      _db.execSQL("DELETE FROM `unsafe_zones`");
      _db.execSQL("DELETE FROM `emergency_contacts`");
      _db.execSQL("DELETE FROM `event_logs`");
      _db.execSQL("DELETE FROM `emergency_services`");
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
    _typeConvertersMap.put(HotspotDao.class, HotspotDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(UnsafeZoneDao.class, UnsafeZoneDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(EmergencyContactDao.class, EmergencyContactDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(EventLogDao.class, EventLogDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(EmergencyServiceDao.class, EmergencyServiceDao_Impl.getRequiredConverters());
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
  public HotspotDao hotspotDao() {
    if (_hotspotDao != null) {
      return _hotspotDao;
    } else {
      synchronized(this) {
        if(_hotspotDao == null) {
          _hotspotDao = new HotspotDao_Impl(this);
        }
        return _hotspotDao;
      }
    }
  }

  @Override
  public UnsafeZoneDao unsafeZoneDao() {
    if (_unsafeZoneDao != null) {
      return _unsafeZoneDao;
    } else {
      synchronized(this) {
        if(_unsafeZoneDao == null) {
          _unsafeZoneDao = new UnsafeZoneDao_Impl(this);
        }
        return _unsafeZoneDao;
      }
    }
  }

  @Override
  public EmergencyContactDao emergencyContactDao() {
    if (_emergencyContactDao != null) {
      return _emergencyContactDao;
    } else {
      synchronized(this) {
        if(_emergencyContactDao == null) {
          _emergencyContactDao = new EmergencyContactDao_Impl(this);
        }
        return _emergencyContactDao;
      }
    }
  }

  @Override
  public EventLogDao eventLogDao() {
    if (_eventLogDao != null) {
      return _eventLogDao;
    } else {
      synchronized(this) {
        if(_eventLogDao == null) {
          _eventLogDao = new EventLogDao_Impl(this);
        }
        return _eventLogDao;
      }
    }
  }

  @Override
  public EmergencyServiceDao emergencyServiceDao() {
    if (_emergencyServiceDao != null) {
      return _emergencyServiceDao;
    } else {
      synchronized(this) {
        if(_emergencyServiceDao == null) {
          _emergencyServiceDao = new EmergencyServiceDao_Impl(this);
        }
        return _emergencyServiceDao;
      }
    }
  }
}
