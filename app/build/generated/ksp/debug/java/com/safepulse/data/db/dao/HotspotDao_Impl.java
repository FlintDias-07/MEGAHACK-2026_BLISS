package com.safepulse.data.db.dao;

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
import androidx.sqlite.db.SupportSQLiteStatement;
import com.safepulse.data.db.entity.HotspotEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
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
public final class HotspotDao_Impl implements HotspotDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<HotspotEntity> __insertionAdapterOfHotspotEntity;

  private final EntityDeletionOrUpdateAdapter<HotspotEntity> __deletionAdapterOfHotspotEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public HotspotDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfHotspotEntity = new EntityInsertionAdapter<HotspotEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `hotspots` (`id`,`lat`,`lng`,`radiusMeters`,`baseRisk`,`timeBucket`,`roadType`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final HotspotEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindDouble(2, entity.getLat());
        statement.bindDouble(3, entity.getLng());
        statement.bindDouble(4, entity.getRadiusMeters());
        statement.bindDouble(5, entity.getBaseRisk());
        statement.bindString(6, entity.getTimeBucket());
        statement.bindString(7, entity.getRoadType());
      }
    };
    this.__deletionAdapterOfHotspotEntity = new EntityDeletionOrUpdateAdapter<HotspotEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `hotspots` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final HotspotEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM hotspots";
        return _query;
      }
    };
  }

  @Override
  public Object insertAll(final List<HotspotEntity> hotspots,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfHotspotEntity.insert(hotspots);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insert(final HotspotEntity hotspot, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfHotspotEntity.insert(hotspot);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final HotspotEntity hotspot, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfHotspotEntity.handle(hotspot);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
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
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<HotspotEntity>> getAllHotspots() {
    final String _sql = "SELECT * FROM hotspots";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"hotspots"}, new Callable<List<HotspotEntity>>() {
      @Override
      @NonNull
      public List<HotspotEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
          final int _cursorIndexOfLng = CursorUtil.getColumnIndexOrThrow(_cursor, "lng");
          final int _cursorIndexOfRadiusMeters = CursorUtil.getColumnIndexOrThrow(_cursor, "radiusMeters");
          final int _cursorIndexOfBaseRisk = CursorUtil.getColumnIndexOrThrow(_cursor, "baseRisk");
          final int _cursorIndexOfTimeBucket = CursorUtil.getColumnIndexOrThrow(_cursor, "timeBucket");
          final int _cursorIndexOfRoadType = CursorUtil.getColumnIndexOrThrow(_cursor, "roadType");
          final List<HotspotEntity> _result = new ArrayList<HotspotEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final HotspotEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final double _tmpLat;
            _tmpLat = _cursor.getDouble(_cursorIndexOfLat);
            final double _tmpLng;
            _tmpLng = _cursor.getDouble(_cursorIndexOfLng);
            final float _tmpRadiusMeters;
            _tmpRadiusMeters = _cursor.getFloat(_cursorIndexOfRadiusMeters);
            final float _tmpBaseRisk;
            _tmpBaseRisk = _cursor.getFloat(_cursorIndexOfBaseRisk);
            final String _tmpTimeBucket;
            _tmpTimeBucket = _cursor.getString(_cursorIndexOfTimeBucket);
            final String _tmpRoadType;
            _tmpRoadType = _cursor.getString(_cursorIndexOfRoadType);
            _item = new HotspotEntity(_tmpId,_tmpLat,_tmpLng,_tmpRadiusMeters,_tmpBaseRisk,_tmpTimeBucket,_tmpRoadType);
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
  public Object getAllHotspotsList(final Continuation<? super List<HotspotEntity>> $completion) {
    final String _sql = "SELECT * FROM hotspots";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<HotspotEntity>>() {
      @Override
      @NonNull
      public List<HotspotEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
          final int _cursorIndexOfLng = CursorUtil.getColumnIndexOrThrow(_cursor, "lng");
          final int _cursorIndexOfRadiusMeters = CursorUtil.getColumnIndexOrThrow(_cursor, "radiusMeters");
          final int _cursorIndexOfBaseRisk = CursorUtil.getColumnIndexOrThrow(_cursor, "baseRisk");
          final int _cursorIndexOfTimeBucket = CursorUtil.getColumnIndexOrThrow(_cursor, "timeBucket");
          final int _cursorIndexOfRoadType = CursorUtil.getColumnIndexOrThrow(_cursor, "roadType");
          final List<HotspotEntity> _result = new ArrayList<HotspotEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final HotspotEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final double _tmpLat;
            _tmpLat = _cursor.getDouble(_cursorIndexOfLat);
            final double _tmpLng;
            _tmpLng = _cursor.getDouble(_cursorIndexOfLng);
            final float _tmpRadiusMeters;
            _tmpRadiusMeters = _cursor.getFloat(_cursorIndexOfRadiusMeters);
            final float _tmpBaseRisk;
            _tmpBaseRisk = _cursor.getFloat(_cursorIndexOfBaseRisk);
            final String _tmpTimeBucket;
            _tmpTimeBucket = _cursor.getString(_cursorIndexOfTimeBucket);
            final String _tmpRoadType;
            _tmpRoadType = _cursor.getString(_cursorIndexOfRoadType);
            _item = new HotspotEntity(_tmpId,_tmpLat,_tmpLng,_tmpRadiusMeters,_tmpBaseRisk,_tmpTimeBucket,_tmpRoadType);
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
  public Object getHotspotsInBounds(final double minLat, final double maxLat, final double minLng,
      final double maxLng, final Continuation<? super List<HotspotEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM hotspots \n"
            + "        WHERE (lat BETWEEN ? AND ?) \n"
            + "        AND (lng BETWEEN ? AND ?)\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 4);
    int _argIndex = 1;
    _statement.bindDouble(_argIndex, minLat);
    _argIndex = 2;
    _statement.bindDouble(_argIndex, maxLat);
    _argIndex = 3;
    _statement.bindDouble(_argIndex, minLng);
    _argIndex = 4;
    _statement.bindDouble(_argIndex, maxLng);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<HotspotEntity>>() {
      @Override
      @NonNull
      public List<HotspotEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfLat = CursorUtil.getColumnIndexOrThrow(_cursor, "lat");
          final int _cursorIndexOfLng = CursorUtil.getColumnIndexOrThrow(_cursor, "lng");
          final int _cursorIndexOfRadiusMeters = CursorUtil.getColumnIndexOrThrow(_cursor, "radiusMeters");
          final int _cursorIndexOfBaseRisk = CursorUtil.getColumnIndexOrThrow(_cursor, "baseRisk");
          final int _cursorIndexOfTimeBucket = CursorUtil.getColumnIndexOrThrow(_cursor, "timeBucket");
          final int _cursorIndexOfRoadType = CursorUtil.getColumnIndexOrThrow(_cursor, "roadType");
          final List<HotspotEntity> _result = new ArrayList<HotspotEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final HotspotEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final double _tmpLat;
            _tmpLat = _cursor.getDouble(_cursorIndexOfLat);
            final double _tmpLng;
            _tmpLng = _cursor.getDouble(_cursorIndexOfLng);
            final float _tmpRadiusMeters;
            _tmpRadiusMeters = _cursor.getFloat(_cursorIndexOfRadiusMeters);
            final float _tmpBaseRisk;
            _tmpBaseRisk = _cursor.getFloat(_cursorIndexOfBaseRisk);
            final String _tmpTimeBucket;
            _tmpTimeBucket = _cursor.getString(_cursorIndexOfTimeBucket);
            final String _tmpRoadType;
            _tmpRoadType = _cursor.getString(_cursorIndexOfRoadType);
            _item = new HotspotEntity(_tmpId,_tmpLat,_tmpLng,_tmpRadiusMeters,_tmpBaseRisk,_tmpTimeBucket,_tmpRoadType);
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
  public Object getCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM hotspots";
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
