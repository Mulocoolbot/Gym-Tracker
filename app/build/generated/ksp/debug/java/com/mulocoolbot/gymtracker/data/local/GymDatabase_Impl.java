package com.mulocoolbot.gymtracker.data.local;

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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class GymDatabase_Impl extends GymDatabase {
  private volatile GymDao _gymDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_profile` (`uid` TEXT NOT NULL, `name` TEXT NOT NULL, `birthDateMillis` INTEGER NOT NULL, `heightCm` REAL NOT NULL, `weightKg` REAL NOT NULL, `currentStreak` INTEGER NOT NULL, `consecutiveMissed` INTEGER NOT NULL, `lastWorkoutEpochDay` INTEGER, `isGoogleLinked` INTEGER NOT NULL, `isEmailLinked` INTEGER NOT NULL, PRIMARY KEY(`uid`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `workout_plan` (`dayOfWeek` INTEGER NOT NULL, `isRestDay` INTEGER NOT NULL, `planName` TEXT NOT NULL, PRIMARY KEY(`dayOfWeek`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `exercise_history` (`normalizedName` TEXT NOT NULL, `muscleGroup` TEXT NOT NULL, `lastUsedMillis` INTEGER NOT NULL, PRIMARY KEY(`normalizedName`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `workout_session` (`sessionId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dateEpochDay` INTEGER NOT NULL, `planName` TEXT NOT NULL, `startTimeMillis` INTEGER NOT NULL, `endTimeMillis` INTEGER, `caloriesBurned` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `workout_set` (`setId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `exerciseName` TEXT NOT NULL, `setType` TEXT NOT NULL, `setOrder` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `reps` INTEGER NOT NULL, `e1rm` REAL NOT NULL, `timestampMillis` INTEGER NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `workout_session`(`sessionId`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_set_sessionId` ON `workout_set` (`sessionId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_set_exerciseName` ON `workout_set` (`exerciseName`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '2773737f49e1e566e511ff43a7cd7f48')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `user_profile`");
        db.execSQL("DROP TABLE IF EXISTS `workout_plan`");
        db.execSQL("DROP TABLE IF EXISTS `exercise_history`");
        db.execSQL("DROP TABLE IF EXISTS `workout_session`");
        db.execSQL("DROP TABLE IF EXISTS `workout_set`");
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
        db.execSQL("PRAGMA foreign_keys = ON");
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
        final HashMap<String, TableInfo.Column> _columnsUserProfile = new HashMap<String, TableInfo.Column>(10);
        _columnsUserProfile.put("uid", new TableInfo.Column("uid", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("birthDateMillis", new TableInfo.Column("birthDateMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("heightCm", new TableInfo.Column("heightCm", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("weightKg", new TableInfo.Column("weightKg", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("currentStreak", new TableInfo.Column("currentStreak", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("consecutiveMissed", new TableInfo.Column("consecutiveMissed", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("lastWorkoutEpochDay", new TableInfo.Column("lastWorkoutEpochDay", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("isGoogleLinked", new TableInfo.Column("isGoogleLinked", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("isEmailLinked", new TableInfo.Column("isEmailLinked", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysUserProfile = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesUserProfile = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoUserProfile = new TableInfo("user_profile", _columnsUserProfile, _foreignKeysUserProfile, _indicesUserProfile);
        final TableInfo _existingUserProfile = TableInfo.read(db, "user_profile");
        if (!_infoUserProfile.equals(_existingUserProfile)) {
          return new RoomOpenHelper.ValidationResult(false, "user_profile(com.mulocoolbot.gymtracker.data.local.UserProfileEntity).\n"
                  + " Expected:\n" + _infoUserProfile + "\n"
                  + " Found:\n" + _existingUserProfile);
        }
        final HashMap<String, TableInfo.Column> _columnsWorkoutPlan = new HashMap<String, TableInfo.Column>(3);
        _columnsWorkoutPlan.put("dayOfWeek", new TableInfo.Column("dayOfWeek", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWorkoutPlan.put("isRestDay", new TableInfo.Column("isRestDay", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWorkoutPlan.put("planName", new TableInfo.Column("planName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysWorkoutPlan = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesWorkoutPlan = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoWorkoutPlan = new TableInfo("workout_plan", _columnsWorkoutPlan, _foreignKeysWorkoutPlan, _indicesWorkoutPlan);
        final TableInfo _existingWorkoutPlan = TableInfo.read(db, "workout_plan");
        if (!_infoWorkoutPlan.equals(_existingWorkoutPlan)) {
          return new RoomOpenHelper.ValidationResult(false, "workout_plan(com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity).\n"
                  + " Expected:\n" + _infoWorkoutPlan + "\n"
                  + " Found:\n" + _existingWorkoutPlan);
        }
        final HashMap<String, TableInfo.Column> _columnsExerciseHistory = new HashMap<String, TableInfo.Column>(3);
        _columnsExerciseHistory.put("normalizedName", new TableInfo.Column("normalizedName", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExerciseHistory.put("muscleGroup", new TableInfo.Column("muscleGroup", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExerciseHistory.put("lastUsedMillis", new TableInfo.Column("lastUsedMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysExerciseHistory = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesExerciseHistory = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoExerciseHistory = new TableInfo("exercise_history", _columnsExerciseHistory, _foreignKeysExerciseHistory, _indicesExerciseHistory);
        final TableInfo _existingExerciseHistory = TableInfo.read(db, "exercise_history");
        if (!_infoExerciseHistory.equals(_existingExerciseHistory)) {
          return new RoomOpenHelper.ValidationResult(false, "exercise_history(com.mulocoolbot.gymtracker.data.local.ExerciseHistoryEntity).\n"
                  + " Expected:\n" + _infoExerciseHistory + "\n"
                  + " Found:\n" + _existingExerciseHistory);
        }
        final HashMap<String, TableInfo.Column> _columnsWorkoutSession = new HashMap<String, TableInfo.Column>(6);
        _columnsWorkoutSession.put("sessionId", new TableInfo.Column("sessionId", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWorkoutSession.put("dateEpochDay", new TableInfo.Column("dateEpochDay", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWorkoutSession.put("planName", new TableInfo.Column("planName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWorkoutSession.put("startTimeMillis", new TableInfo.Column("startTimeMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWorkoutSession.put("endTimeMillis", new TableInfo.Column("endTimeMillis", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWorkoutSession.put("caloriesBurned", new TableInfo.Column("caloriesBurned", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysWorkoutSession = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesWorkoutSession = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoWorkoutSession = new TableInfo("workout_session", _columnsWorkoutSession, _foreignKeysWorkoutSession, _indicesWorkoutSession);
        final TableInfo _existingWorkoutSession = TableInfo.read(db, "workout_session");
        if (!_infoWorkoutSession.equals(_existingWorkoutSession)) {
          return new RoomOpenHelper.ValidationResult(false, "workout_session(com.mulocoolbot.gymtracker.data.local.WorkoutSessionEntity).\n"
                  + " Expected:\n" + _infoWorkoutSession + "\n"
                  + " Found:\n" + _existingWorkoutSession);
        }
        final HashMap<String, TableInfo.Column> _columnsWorkoutSet = new HashMap<String, TableInfo.Column>(9);
        _columnsWorkoutSet.put("setId", new TableInfo.Column("setId", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWorkoutSet.put("sessionId", new TableInfo.Column("sessionId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWorkoutSet.put("exerciseName", new TableInfo.Column("exerciseName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWorkoutSet.put("setType", new TableInfo.Column("setType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWorkoutSet.put("setOrder", new TableInfo.Column("setOrder", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWorkoutSet.put("weightKg", new TableInfo.Column("weightKg", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWorkoutSet.put("reps", new TableInfo.Column("reps", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWorkoutSet.put("e1rm", new TableInfo.Column("e1rm", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWorkoutSet.put("timestampMillis", new TableInfo.Column("timestampMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysWorkoutSet = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysWorkoutSet.add(new TableInfo.ForeignKey("workout_session", "CASCADE", "NO ACTION", Arrays.asList("sessionId"), Arrays.asList("sessionId")));
        final HashSet<TableInfo.Index> _indicesWorkoutSet = new HashSet<TableInfo.Index>(2);
        _indicesWorkoutSet.add(new TableInfo.Index("index_workout_set_sessionId", false, Arrays.asList("sessionId"), Arrays.asList("ASC")));
        _indicesWorkoutSet.add(new TableInfo.Index("index_workout_set_exerciseName", false, Arrays.asList("exerciseName"), Arrays.asList("ASC")));
        final TableInfo _infoWorkoutSet = new TableInfo("workout_set", _columnsWorkoutSet, _foreignKeysWorkoutSet, _indicesWorkoutSet);
        final TableInfo _existingWorkoutSet = TableInfo.read(db, "workout_set");
        if (!_infoWorkoutSet.equals(_existingWorkoutSet)) {
          return new RoomOpenHelper.ValidationResult(false, "workout_set(com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity).\n"
                  + " Expected:\n" + _infoWorkoutSet + "\n"
                  + " Found:\n" + _existingWorkoutSet);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "2773737f49e1e566e511ff43a7cd7f48", "5b3b408f6dc7f9589bf2482b50711d50");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "user_profile","workout_plan","exercise_history","workout_session","workout_set");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `user_profile`");
      _db.execSQL("DELETE FROM `workout_plan`");
      _db.execSQL("DELETE FROM `exercise_history`");
      _db.execSQL("DELETE FROM `workout_session`");
      _db.execSQL("DELETE FROM `workout_set`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
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
    _typeConvertersMap.put(GymDao.class, GymDao_Impl.getRequiredConverters());
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
  public GymDao gymDao() {
    if (_gymDao != null) {
      return _gymDao;
    } else {
      synchronized(this) {
        if(_gymDao == null) {
          _gymDao = new GymDao_Impl(this);
        }
        return _gymDao;
      }
    }
  }
}
