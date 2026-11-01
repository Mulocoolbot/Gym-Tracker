package com.mulocoolbot.gymtracker.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.EntityUpsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
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
public final class GymDao_Impl implements GymDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<WorkoutSessionEntity> __insertionAdapterOfWorkoutSessionEntity;

  private final EntityInsertionAdapter<WorkoutSetEntity> __insertionAdapterOfWorkoutSetEntity;

  private final EntityDeletionOrUpdateAdapter<WorkoutSessionEntity> __deletionAdapterOfWorkoutSessionEntity;

  private final EntityDeletionOrUpdateAdapter<WorkoutSetEntity> __deletionAdapterOfWorkoutSetEntity;

  private final EntityDeletionOrUpdateAdapter<WorkoutSessionEntity> __updateAdapterOfWorkoutSessionEntity;

  private final SharedSQLiteStatement __preparedStmtOfClearUserProfile;

  private final EntityUpsertionAdapter<UserProfileEntity> __upsertionAdapterOfUserProfileEntity;

  private final EntityUpsertionAdapter<WorkoutPlanEntity> __upsertionAdapterOfWorkoutPlanEntity;

  private final EntityUpsertionAdapter<ExerciseHistoryEntity> __upsertionAdapterOfExerciseHistoryEntity;

  public GymDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfWorkoutSessionEntity = new EntityInsertionAdapter<WorkoutSessionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `workout_session` (`sessionId`,`dateEpochDay`,`planName`,`startTimeMillis`,`endTimeMillis`,`caloriesBurned`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final WorkoutSessionEntity entity) {
        statement.bindLong(1, entity.getSessionId());
        statement.bindLong(2, entity.getDateEpochDay());
        statement.bindString(3, entity.getPlanName());
        statement.bindLong(4, entity.getStartTimeMillis());
        if (entity.getEndTimeMillis() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getEndTimeMillis());
        }
        statement.bindLong(6, entity.getCaloriesBurned());
      }
    };
    this.__insertionAdapterOfWorkoutSetEntity = new EntityInsertionAdapter<WorkoutSetEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `workout_set` (`setId`,`sessionId`,`exerciseName`,`setType`,`setOrder`,`weightKg`,`reps`,`e1rm`,`timestampMillis`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final WorkoutSetEntity entity) {
        statement.bindLong(1, entity.getSetId());
        statement.bindLong(2, entity.getSessionId());
        statement.bindString(3, entity.getExerciseName());
        statement.bindString(4, entity.getSetType());
        statement.bindLong(5, entity.getSetOrder());
        statement.bindDouble(6, entity.getWeightKg());
        statement.bindLong(7, entity.getReps());
        statement.bindDouble(8, entity.getE1rm());
        statement.bindLong(9, entity.getTimestampMillis());
      }
    };
    this.__deletionAdapterOfWorkoutSessionEntity = new EntityDeletionOrUpdateAdapter<WorkoutSessionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `workout_session` WHERE `sessionId` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final WorkoutSessionEntity entity) {
        statement.bindLong(1, entity.getSessionId());
      }
    };
    this.__deletionAdapterOfWorkoutSetEntity = new EntityDeletionOrUpdateAdapter<WorkoutSetEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `workout_set` WHERE `setId` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final WorkoutSetEntity entity) {
        statement.bindLong(1, entity.getSetId());
      }
    };
    this.__updateAdapterOfWorkoutSessionEntity = new EntityDeletionOrUpdateAdapter<WorkoutSessionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `workout_session` SET `sessionId` = ?,`dateEpochDay` = ?,`planName` = ?,`startTimeMillis` = ?,`endTimeMillis` = ?,`caloriesBurned` = ? WHERE `sessionId` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final WorkoutSessionEntity entity) {
        statement.bindLong(1, entity.getSessionId());
        statement.bindLong(2, entity.getDateEpochDay());
        statement.bindString(3, entity.getPlanName());
        statement.bindLong(4, entity.getStartTimeMillis());
        if (entity.getEndTimeMillis() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getEndTimeMillis());
        }
        statement.bindLong(6, entity.getCaloriesBurned());
        statement.bindLong(7, entity.getSessionId());
      }
    };
    this.__preparedStmtOfClearUserProfile = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM user_profile";
        return _query;
      }
    };
    this.__upsertionAdapterOfUserProfileEntity = new EntityUpsertionAdapter<UserProfileEntity>(new EntityInsertionAdapter<UserProfileEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `user_profile` (`uid`,`name`,`birthDateMillis`,`heightCm`,`weightKg`,`currentStreak`,`consecutiveMissed`,`lastWorkoutEpochDay`,`isGoogleLinked`,`isEmailLinked`) VALUES (?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final UserProfileEntity entity) {
        statement.bindString(1, entity.getUid());
        statement.bindString(2, entity.getName());
        statement.bindLong(3, entity.getBirthDateMillis());
        statement.bindDouble(4, entity.getHeightCm());
        statement.bindDouble(5, entity.getWeightKg());
        statement.bindLong(6, entity.getCurrentStreak());
        statement.bindLong(7, entity.getConsecutiveMissed());
        if (entity.getLastWorkoutEpochDay() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getLastWorkoutEpochDay());
        }
        final int _tmp = entity.isGoogleLinked() ? 1 : 0;
        statement.bindLong(9, _tmp);
        final int _tmp_1 = entity.isEmailLinked() ? 1 : 0;
        statement.bindLong(10, _tmp_1);
      }
    }, new EntityDeletionOrUpdateAdapter<UserProfileEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `user_profile` SET `uid` = ?,`name` = ?,`birthDateMillis` = ?,`heightCm` = ?,`weightKg` = ?,`currentStreak` = ?,`consecutiveMissed` = ?,`lastWorkoutEpochDay` = ?,`isGoogleLinked` = ?,`isEmailLinked` = ? WHERE `uid` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final UserProfileEntity entity) {
        statement.bindString(1, entity.getUid());
        statement.bindString(2, entity.getName());
        statement.bindLong(3, entity.getBirthDateMillis());
        statement.bindDouble(4, entity.getHeightCm());
        statement.bindDouble(5, entity.getWeightKg());
        statement.bindLong(6, entity.getCurrentStreak());
        statement.bindLong(7, entity.getConsecutiveMissed());
        if (entity.getLastWorkoutEpochDay() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getLastWorkoutEpochDay());
        }
        final int _tmp = entity.isGoogleLinked() ? 1 : 0;
        statement.bindLong(9, _tmp);
        final int _tmp_1 = entity.isEmailLinked() ? 1 : 0;
        statement.bindLong(10, _tmp_1);
        statement.bindString(11, entity.getUid());
      }
    });
    this.__upsertionAdapterOfWorkoutPlanEntity = new EntityUpsertionAdapter<WorkoutPlanEntity>(new EntityInsertionAdapter<WorkoutPlanEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `workout_plan` (`dayOfWeek`,`isRestDay`,`planName`) VALUES (?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final WorkoutPlanEntity entity) {
        statement.bindLong(1, entity.getDayOfWeek());
        final int _tmp = entity.isRestDay() ? 1 : 0;
        statement.bindLong(2, _tmp);
        statement.bindString(3, entity.getPlanName());
      }
    }, new EntityDeletionOrUpdateAdapter<WorkoutPlanEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `workout_plan` SET `dayOfWeek` = ?,`isRestDay` = ?,`planName` = ? WHERE `dayOfWeek` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final WorkoutPlanEntity entity) {
        statement.bindLong(1, entity.getDayOfWeek());
        final int _tmp = entity.isRestDay() ? 1 : 0;
        statement.bindLong(2, _tmp);
        statement.bindString(3, entity.getPlanName());
        statement.bindLong(4, entity.getDayOfWeek());
      }
    });
    this.__upsertionAdapterOfExerciseHistoryEntity = new EntityUpsertionAdapter<ExerciseHistoryEntity>(new EntityInsertionAdapter<ExerciseHistoryEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `exercise_history` (`normalizedName`,`muscleGroup`,`lastUsedMillis`) VALUES (?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ExerciseHistoryEntity entity) {
        statement.bindString(1, entity.getNormalizedName());
        statement.bindString(2, entity.getMuscleGroup());
        statement.bindLong(3, entity.getLastUsedMillis());
      }
    }, new EntityDeletionOrUpdateAdapter<ExerciseHistoryEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `exercise_history` SET `normalizedName` = ?,`muscleGroup` = ?,`lastUsedMillis` = ? WHERE `normalizedName` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ExerciseHistoryEntity entity) {
        statement.bindString(1, entity.getNormalizedName());
        statement.bindString(2, entity.getMuscleGroup());
        statement.bindLong(3, entity.getLastUsedMillis());
        statement.bindString(4, entity.getNormalizedName());
      }
    });
  }

  @Override
  public Object insertSession(final WorkoutSessionEntity session,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfWorkoutSessionEntity.insertAndReturnId(session);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertSet(final WorkoutSetEntity workoutSet,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfWorkoutSetEntity.insertAndReturnId(workoutSet);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertSets(final List<WorkoutSetEntity> sets,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfWorkoutSetEntity.insert(sets);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteSession(final WorkoutSessionEntity session,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfWorkoutSessionEntity.handle(session);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteSet(final WorkoutSetEntity workoutSet,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfWorkoutSetEntity.handle(workoutSet);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateSession(final WorkoutSessionEntity session,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfWorkoutSessionEntity.handle(session);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object clearUserProfile(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearUserProfile.acquire();
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
          __preparedStmtOfClearUserProfile.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertUserProfile(final UserProfileEntity profile,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfUserProfileEntity.upsert(profile);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertWorkoutPlans(final List<WorkoutPlanEntity> plans,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfWorkoutPlanEntity.upsert(plans);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertWorkoutPlan(final WorkoutPlanEntity plan,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfWorkoutPlanEntity.upsert(plan);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertExerciseHistory(final ExerciseHistoryEntity exercise,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfExerciseHistoryEntity.upsert(exercise);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertExerciseHistories(final List<ExerciseHistoryEntity> exercises,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfExerciseHistoryEntity.upsert(exercises);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<UserProfileEntity> observeUserProfile() {
    final String _sql = "SELECT * FROM user_profile LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"user_profile"}, new Callable<UserProfileEntity>() {
      @Override
      @Nullable
      public UserProfileEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfUid = CursorUtil.getColumnIndexOrThrow(_cursor, "uid");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfBirthDateMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "birthDateMillis");
          final int _cursorIndexOfHeightCm = CursorUtil.getColumnIndexOrThrow(_cursor, "heightCm");
          final int _cursorIndexOfWeightKg = CursorUtil.getColumnIndexOrThrow(_cursor, "weightKg");
          final int _cursorIndexOfCurrentStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "currentStreak");
          final int _cursorIndexOfConsecutiveMissed = CursorUtil.getColumnIndexOrThrow(_cursor, "consecutiveMissed");
          final int _cursorIndexOfLastWorkoutEpochDay = CursorUtil.getColumnIndexOrThrow(_cursor, "lastWorkoutEpochDay");
          final int _cursorIndexOfIsGoogleLinked = CursorUtil.getColumnIndexOrThrow(_cursor, "isGoogleLinked");
          final int _cursorIndexOfIsEmailLinked = CursorUtil.getColumnIndexOrThrow(_cursor, "isEmailLinked");
          final UserProfileEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpUid;
            _tmpUid = _cursor.getString(_cursorIndexOfUid);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final long _tmpBirthDateMillis;
            _tmpBirthDateMillis = _cursor.getLong(_cursorIndexOfBirthDateMillis);
            final double _tmpHeightCm;
            _tmpHeightCm = _cursor.getDouble(_cursorIndexOfHeightCm);
            final double _tmpWeightKg;
            _tmpWeightKg = _cursor.getDouble(_cursorIndexOfWeightKg);
            final int _tmpCurrentStreak;
            _tmpCurrentStreak = _cursor.getInt(_cursorIndexOfCurrentStreak);
            final int _tmpConsecutiveMissed;
            _tmpConsecutiveMissed = _cursor.getInt(_cursorIndexOfConsecutiveMissed);
            final Long _tmpLastWorkoutEpochDay;
            if (_cursor.isNull(_cursorIndexOfLastWorkoutEpochDay)) {
              _tmpLastWorkoutEpochDay = null;
            } else {
              _tmpLastWorkoutEpochDay = _cursor.getLong(_cursorIndexOfLastWorkoutEpochDay);
            }
            final boolean _tmpIsGoogleLinked;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsGoogleLinked);
            _tmpIsGoogleLinked = _tmp != 0;
            final boolean _tmpIsEmailLinked;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsEmailLinked);
            _tmpIsEmailLinked = _tmp_1 != 0;
            _result = new UserProfileEntity(_tmpUid,_tmpName,_tmpBirthDateMillis,_tmpHeightCm,_tmpWeightKg,_tmpCurrentStreak,_tmpConsecutiveMissed,_tmpLastWorkoutEpochDay,_tmpIsGoogleLinked,_tmpIsEmailLinked);
          } else {
            _result = null;
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
  public Object getUserProfile(final Continuation<? super UserProfileEntity> $completion) {
    final String _sql = "SELECT * FROM user_profile LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<UserProfileEntity>() {
      @Override
      @Nullable
      public UserProfileEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfUid = CursorUtil.getColumnIndexOrThrow(_cursor, "uid");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfBirthDateMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "birthDateMillis");
          final int _cursorIndexOfHeightCm = CursorUtil.getColumnIndexOrThrow(_cursor, "heightCm");
          final int _cursorIndexOfWeightKg = CursorUtil.getColumnIndexOrThrow(_cursor, "weightKg");
          final int _cursorIndexOfCurrentStreak = CursorUtil.getColumnIndexOrThrow(_cursor, "currentStreak");
          final int _cursorIndexOfConsecutiveMissed = CursorUtil.getColumnIndexOrThrow(_cursor, "consecutiveMissed");
          final int _cursorIndexOfLastWorkoutEpochDay = CursorUtil.getColumnIndexOrThrow(_cursor, "lastWorkoutEpochDay");
          final int _cursorIndexOfIsGoogleLinked = CursorUtil.getColumnIndexOrThrow(_cursor, "isGoogleLinked");
          final int _cursorIndexOfIsEmailLinked = CursorUtil.getColumnIndexOrThrow(_cursor, "isEmailLinked");
          final UserProfileEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpUid;
            _tmpUid = _cursor.getString(_cursorIndexOfUid);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final long _tmpBirthDateMillis;
            _tmpBirthDateMillis = _cursor.getLong(_cursorIndexOfBirthDateMillis);
            final double _tmpHeightCm;
            _tmpHeightCm = _cursor.getDouble(_cursorIndexOfHeightCm);
            final double _tmpWeightKg;
            _tmpWeightKg = _cursor.getDouble(_cursorIndexOfWeightKg);
            final int _tmpCurrentStreak;
            _tmpCurrentStreak = _cursor.getInt(_cursorIndexOfCurrentStreak);
            final int _tmpConsecutiveMissed;
            _tmpConsecutiveMissed = _cursor.getInt(_cursorIndexOfConsecutiveMissed);
            final Long _tmpLastWorkoutEpochDay;
            if (_cursor.isNull(_cursorIndexOfLastWorkoutEpochDay)) {
              _tmpLastWorkoutEpochDay = null;
            } else {
              _tmpLastWorkoutEpochDay = _cursor.getLong(_cursorIndexOfLastWorkoutEpochDay);
            }
            final boolean _tmpIsGoogleLinked;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsGoogleLinked);
            _tmpIsGoogleLinked = _tmp != 0;
            final boolean _tmpIsEmailLinked;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsEmailLinked);
            _tmpIsEmailLinked = _tmp_1 != 0;
            _result = new UserProfileEntity(_tmpUid,_tmpName,_tmpBirthDateMillis,_tmpHeightCm,_tmpWeightKg,_tmpCurrentStreak,_tmpConsecutiveMissed,_tmpLastWorkoutEpochDay,_tmpIsGoogleLinked,_tmpIsEmailLinked);
          } else {
            _result = null;
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
  public Flow<List<WorkoutPlanEntity>> observeWorkoutPlans() {
    final String _sql = "SELECT * FROM workout_plan ORDER BY dayOfWeek ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"workout_plan"}, new Callable<List<WorkoutPlanEntity>>() {
      @Override
      @NonNull
      public List<WorkoutPlanEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDayOfWeek = CursorUtil.getColumnIndexOrThrow(_cursor, "dayOfWeek");
          final int _cursorIndexOfIsRestDay = CursorUtil.getColumnIndexOrThrow(_cursor, "isRestDay");
          final int _cursorIndexOfPlanName = CursorUtil.getColumnIndexOrThrow(_cursor, "planName");
          final List<WorkoutPlanEntity> _result = new ArrayList<WorkoutPlanEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final WorkoutPlanEntity _item;
            final int _tmpDayOfWeek;
            _tmpDayOfWeek = _cursor.getInt(_cursorIndexOfDayOfWeek);
            final boolean _tmpIsRestDay;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsRestDay);
            _tmpIsRestDay = _tmp != 0;
            final String _tmpPlanName;
            _tmpPlanName = _cursor.getString(_cursorIndexOfPlanName);
            _item = new WorkoutPlanEntity(_tmpDayOfWeek,_tmpIsRestDay,_tmpPlanName);
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
  public Object getWorkoutPlans(final Continuation<? super List<WorkoutPlanEntity>> $completion) {
    final String _sql = "SELECT * FROM workout_plan ORDER BY dayOfWeek ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<WorkoutPlanEntity>>() {
      @Override
      @NonNull
      public List<WorkoutPlanEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDayOfWeek = CursorUtil.getColumnIndexOrThrow(_cursor, "dayOfWeek");
          final int _cursorIndexOfIsRestDay = CursorUtil.getColumnIndexOrThrow(_cursor, "isRestDay");
          final int _cursorIndexOfPlanName = CursorUtil.getColumnIndexOrThrow(_cursor, "planName");
          final List<WorkoutPlanEntity> _result = new ArrayList<WorkoutPlanEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final WorkoutPlanEntity _item;
            final int _tmpDayOfWeek;
            _tmpDayOfWeek = _cursor.getInt(_cursorIndexOfDayOfWeek);
            final boolean _tmpIsRestDay;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsRestDay);
            _tmpIsRestDay = _tmp != 0;
            final String _tmpPlanName;
            _tmpPlanName = _cursor.getString(_cursorIndexOfPlanName);
            _item = new WorkoutPlanEntity(_tmpDayOfWeek,_tmpIsRestDay,_tmpPlanName);
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
  public Flow<List<ExerciseHistoryEntity>> observeExerciseHistory() {
    final String _sql = "SELECT * FROM exercise_history ORDER BY lastUsedMillis DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"exercise_history"}, new Callable<List<ExerciseHistoryEntity>>() {
      @Override
      @NonNull
      public List<ExerciseHistoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfNormalizedName = CursorUtil.getColumnIndexOrThrow(_cursor, "normalizedName");
          final int _cursorIndexOfMuscleGroup = CursorUtil.getColumnIndexOrThrow(_cursor, "muscleGroup");
          final int _cursorIndexOfLastUsedMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUsedMillis");
          final List<ExerciseHistoryEntity> _result = new ArrayList<ExerciseHistoryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ExerciseHistoryEntity _item;
            final String _tmpNormalizedName;
            _tmpNormalizedName = _cursor.getString(_cursorIndexOfNormalizedName);
            final String _tmpMuscleGroup;
            _tmpMuscleGroup = _cursor.getString(_cursorIndexOfMuscleGroup);
            final long _tmpLastUsedMillis;
            _tmpLastUsedMillis = _cursor.getLong(_cursorIndexOfLastUsedMillis);
            _item = new ExerciseHistoryEntity(_tmpNormalizedName,_tmpMuscleGroup,_tmpLastUsedMillis);
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
  public Object getSessionById(final long sessionId,
      final Continuation<? super WorkoutSessionEntity> $completion) {
    final String _sql = "SELECT * FROM workout_session WHERE sessionId = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, sessionId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<WorkoutSessionEntity>() {
      @Override
      @Nullable
      public WorkoutSessionEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfDateEpochDay = CursorUtil.getColumnIndexOrThrow(_cursor, "dateEpochDay");
          final int _cursorIndexOfPlanName = CursorUtil.getColumnIndexOrThrow(_cursor, "planName");
          final int _cursorIndexOfStartTimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "startTimeMillis");
          final int _cursorIndexOfEndTimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "endTimeMillis");
          final int _cursorIndexOfCaloriesBurned = CursorUtil.getColumnIndexOrThrow(_cursor, "caloriesBurned");
          final WorkoutSessionEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpSessionId;
            _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            final long _tmpDateEpochDay;
            _tmpDateEpochDay = _cursor.getLong(_cursorIndexOfDateEpochDay);
            final String _tmpPlanName;
            _tmpPlanName = _cursor.getString(_cursorIndexOfPlanName);
            final long _tmpStartTimeMillis;
            _tmpStartTimeMillis = _cursor.getLong(_cursorIndexOfStartTimeMillis);
            final Long _tmpEndTimeMillis;
            if (_cursor.isNull(_cursorIndexOfEndTimeMillis)) {
              _tmpEndTimeMillis = null;
            } else {
              _tmpEndTimeMillis = _cursor.getLong(_cursorIndexOfEndTimeMillis);
            }
            final int _tmpCaloriesBurned;
            _tmpCaloriesBurned = _cursor.getInt(_cursorIndexOfCaloriesBurned);
            _result = new WorkoutSessionEntity(_tmpSessionId,_tmpDateEpochDay,_tmpPlanName,_tmpStartTimeMillis,_tmpEndTimeMillis,_tmpCaloriesBurned);
          } else {
            _result = null;
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
  public Flow<List<WorkoutSessionEntity>> observeSessionsByDate(final long dateEpochDay) {
    final String _sql = "SELECT * FROM workout_session WHERE dateEpochDay = ? ORDER BY startTimeMillis DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, dateEpochDay);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"workout_session"}, new Callable<List<WorkoutSessionEntity>>() {
      @Override
      @NonNull
      public List<WorkoutSessionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfDateEpochDay = CursorUtil.getColumnIndexOrThrow(_cursor, "dateEpochDay");
          final int _cursorIndexOfPlanName = CursorUtil.getColumnIndexOrThrow(_cursor, "planName");
          final int _cursorIndexOfStartTimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "startTimeMillis");
          final int _cursorIndexOfEndTimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "endTimeMillis");
          final int _cursorIndexOfCaloriesBurned = CursorUtil.getColumnIndexOrThrow(_cursor, "caloriesBurned");
          final List<WorkoutSessionEntity> _result = new ArrayList<WorkoutSessionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final WorkoutSessionEntity _item;
            final long _tmpSessionId;
            _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            final long _tmpDateEpochDay;
            _tmpDateEpochDay = _cursor.getLong(_cursorIndexOfDateEpochDay);
            final String _tmpPlanName;
            _tmpPlanName = _cursor.getString(_cursorIndexOfPlanName);
            final long _tmpStartTimeMillis;
            _tmpStartTimeMillis = _cursor.getLong(_cursorIndexOfStartTimeMillis);
            final Long _tmpEndTimeMillis;
            if (_cursor.isNull(_cursorIndexOfEndTimeMillis)) {
              _tmpEndTimeMillis = null;
            } else {
              _tmpEndTimeMillis = _cursor.getLong(_cursorIndexOfEndTimeMillis);
            }
            final int _tmpCaloriesBurned;
            _tmpCaloriesBurned = _cursor.getInt(_cursorIndexOfCaloriesBurned);
            _item = new WorkoutSessionEntity(_tmpSessionId,_tmpDateEpochDay,_tmpPlanName,_tmpStartTimeMillis,_tmpEndTimeMillis,_tmpCaloriesBurned);
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
  public Flow<List<WorkoutSessionEntity>> observeAllSessions() {
    final String _sql = "SELECT * FROM workout_session ORDER BY startTimeMillis DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"workout_session"}, new Callable<List<WorkoutSessionEntity>>() {
      @Override
      @NonNull
      public List<WorkoutSessionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfDateEpochDay = CursorUtil.getColumnIndexOrThrow(_cursor, "dateEpochDay");
          final int _cursorIndexOfPlanName = CursorUtil.getColumnIndexOrThrow(_cursor, "planName");
          final int _cursorIndexOfStartTimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "startTimeMillis");
          final int _cursorIndexOfEndTimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "endTimeMillis");
          final int _cursorIndexOfCaloriesBurned = CursorUtil.getColumnIndexOrThrow(_cursor, "caloriesBurned");
          final List<WorkoutSessionEntity> _result = new ArrayList<WorkoutSessionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final WorkoutSessionEntity _item;
            final long _tmpSessionId;
            _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            final long _tmpDateEpochDay;
            _tmpDateEpochDay = _cursor.getLong(_cursorIndexOfDateEpochDay);
            final String _tmpPlanName;
            _tmpPlanName = _cursor.getString(_cursorIndexOfPlanName);
            final long _tmpStartTimeMillis;
            _tmpStartTimeMillis = _cursor.getLong(_cursorIndexOfStartTimeMillis);
            final Long _tmpEndTimeMillis;
            if (_cursor.isNull(_cursorIndexOfEndTimeMillis)) {
              _tmpEndTimeMillis = null;
            } else {
              _tmpEndTimeMillis = _cursor.getLong(_cursorIndexOfEndTimeMillis);
            }
            final int _tmpCaloriesBurned;
            _tmpCaloriesBurned = _cursor.getInt(_cursorIndexOfCaloriesBurned);
            _item = new WorkoutSessionEntity(_tmpSessionId,_tmpDateEpochDay,_tmpPlanName,_tmpStartTimeMillis,_tmpEndTimeMillis,_tmpCaloriesBurned);
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
  public Flow<Integer> observeCaloriesByDate(final long dateEpochDay) {
    final String _sql = "SELECT COALESCE(SUM(caloriesBurned), 0) FROM workout_session WHERE dateEpochDay = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, dateEpochDay);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"workout_session"}, new Callable<Integer>() {
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
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<WorkoutSetEntity>> observeSetsForSession(final long sessionId) {
    final String _sql = "SELECT * FROM workout_set WHERE sessionId = ? ORDER BY setOrder ASC, timestampMillis ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, sessionId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"workout_set"}, new Callable<List<WorkoutSetEntity>>() {
      @Override
      @NonNull
      public List<WorkoutSetEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSetId = CursorUtil.getColumnIndexOrThrow(_cursor, "setId");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfExerciseName = CursorUtil.getColumnIndexOrThrow(_cursor, "exerciseName");
          final int _cursorIndexOfSetType = CursorUtil.getColumnIndexOrThrow(_cursor, "setType");
          final int _cursorIndexOfSetOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "setOrder");
          final int _cursorIndexOfWeightKg = CursorUtil.getColumnIndexOrThrow(_cursor, "weightKg");
          final int _cursorIndexOfReps = CursorUtil.getColumnIndexOrThrow(_cursor, "reps");
          final int _cursorIndexOfE1rm = CursorUtil.getColumnIndexOrThrow(_cursor, "e1rm");
          final int _cursorIndexOfTimestampMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "timestampMillis");
          final List<WorkoutSetEntity> _result = new ArrayList<WorkoutSetEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final WorkoutSetEntity _item;
            final long _tmpSetId;
            _tmpSetId = _cursor.getLong(_cursorIndexOfSetId);
            final long _tmpSessionId;
            _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            final String _tmpExerciseName;
            _tmpExerciseName = _cursor.getString(_cursorIndexOfExerciseName);
            final String _tmpSetType;
            _tmpSetType = _cursor.getString(_cursorIndexOfSetType);
            final int _tmpSetOrder;
            _tmpSetOrder = _cursor.getInt(_cursorIndexOfSetOrder);
            final double _tmpWeightKg;
            _tmpWeightKg = _cursor.getDouble(_cursorIndexOfWeightKg);
            final int _tmpReps;
            _tmpReps = _cursor.getInt(_cursorIndexOfReps);
            final double _tmpE1rm;
            _tmpE1rm = _cursor.getDouble(_cursorIndexOfE1rm);
            final long _tmpTimestampMillis;
            _tmpTimestampMillis = _cursor.getLong(_cursorIndexOfTimestampMillis);
            _item = new WorkoutSetEntity(_tmpSetId,_tmpSessionId,_tmpExerciseName,_tmpSetType,_tmpSetOrder,_tmpWeightKg,_tmpReps,_tmpE1rm,_tmpTimestampMillis);
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
  public Object getSetsForSession(final long sessionId,
      final Continuation<? super List<WorkoutSetEntity>> $completion) {
    final String _sql = "SELECT * FROM workout_set WHERE sessionId = ? ORDER BY setOrder ASC, timestampMillis ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, sessionId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<WorkoutSetEntity>>() {
      @Override
      @NonNull
      public List<WorkoutSetEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSetId = CursorUtil.getColumnIndexOrThrow(_cursor, "setId");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfExerciseName = CursorUtil.getColumnIndexOrThrow(_cursor, "exerciseName");
          final int _cursorIndexOfSetType = CursorUtil.getColumnIndexOrThrow(_cursor, "setType");
          final int _cursorIndexOfSetOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "setOrder");
          final int _cursorIndexOfWeightKg = CursorUtil.getColumnIndexOrThrow(_cursor, "weightKg");
          final int _cursorIndexOfReps = CursorUtil.getColumnIndexOrThrow(_cursor, "reps");
          final int _cursorIndexOfE1rm = CursorUtil.getColumnIndexOrThrow(_cursor, "e1rm");
          final int _cursorIndexOfTimestampMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "timestampMillis");
          final List<WorkoutSetEntity> _result = new ArrayList<WorkoutSetEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final WorkoutSetEntity _item;
            final long _tmpSetId;
            _tmpSetId = _cursor.getLong(_cursorIndexOfSetId);
            final long _tmpSessionId;
            _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            final String _tmpExerciseName;
            _tmpExerciseName = _cursor.getString(_cursorIndexOfExerciseName);
            final String _tmpSetType;
            _tmpSetType = _cursor.getString(_cursorIndexOfSetType);
            final int _tmpSetOrder;
            _tmpSetOrder = _cursor.getInt(_cursorIndexOfSetOrder);
            final double _tmpWeightKg;
            _tmpWeightKg = _cursor.getDouble(_cursorIndexOfWeightKg);
            final int _tmpReps;
            _tmpReps = _cursor.getInt(_cursorIndexOfReps);
            final double _tmpE1rm;
            _tmpE1rm = _cursor.getDouble(_cursorIndexOfE1rm);
            final long _tmpTimestampMillis;
            _tmpTimestampMillis = _cursor.getLong(_cursorIndexOfTimestampMillis);
            _item = new WorkoutSetEntity(_tmpSetId,_tmpSessionId,_tmpExerciseName,_tmpSetType,_tmpSetOrder,_tmpWeightKg,_tmpReps,_tmpE1rm,_tmpTimestampMillis);
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
  public Flow<List<WorkoutSetEntity>> observeSetsByDate(final long dateEpochDay) {
    final String _sql = "\n"
            + "        SELECT ws.* FROM workout_set ws\n"
            + "        INNER JOIN workout_session s ON ws.sessionId = s.sessionId\n"
            + "        WHERE s.dateEpochDay = ?\n"
            + "        ORDER BY ws.timestampMillis ASC\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, dateEpochDay);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"workout_set",
        "workout_session"}, new Callable<List<WorkoutSetEntity>>() {
      @Override
      @NonNull
      public List<WorkoutSetEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSetId = CursorUtil.getColumnIndexOrThrow(_cursor, "setId");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfExerciseName = CursorUtil.getColumnIndexOrThrow(_cursor, "exerciseName");
          final int _cursorIndexOfSetType = CursorUtil.getColumnIndexOrThrow(_cursor, "setType");
          final int _cursorIndexOfSetOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "setOrder");
          final int _cursorIndexOfWeightKg = CursorUtil.getColumnIndexOrThrow(_cursor, "weightKg");
          final int _cursorIndexOfReps = CursorUtil.getColumnIndexOrThrow(_cursor, "reps");
          final int _cursorIndexOfE1rm = CursorUtil.getColumnIndexOrThrow(_cursor, "e1rm");
          final int _cursorIndexOfTimestampMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "timestampMillis");
          final List<WorkoutSetEntity> _result = new ArrayList<WorkoutSetEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final WorkoutSetEntity _item;
            final long _tmpSetId;
            _tmpSetId = _cursor.getLong(_cursorIndexOfSetId);
            final long _tmpSessionId;
            _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            final String _tmpExerciseName;
            _tmpExerciseName = _cursor.getString(_cursorIndexOfExerciseName);
            final String _tmpSetType;
            _tmpSetType = _cursor.getString(_cursorIndexOfSetType);
            final int _tmpSetOrder;
            _tmpSetOrder = _cursor.getInt(_cursorIndexOfSetOrder);
            final double _tmpWeightKg;
            _tmpWeightKg = _cursor.getDouble(_cursorIndexOfWeightKg);
            final int _tmpReps;
            _tmpReps = _cursor.getInt(_cursorIndexOfReps);
            final double _tmpE1rm;
            _tmpE1rm = _cursor.getDouble(_cursorIndexOfE1rm);
            final long _tmpTimestampMillis;
            _tmpTimestampMillis = _cursor.getLong(_cursorIndexOfTimestampMillis);
            _item = new WorkoutSetEntity(_tmpSetId,_tmpSessionId,_tmpExerciseName,_tmpSetType,_tmpSetOrder,_tmpWeightKg,_tmpReps,_tmpE1rm,_tmpTimestampMillis);
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
  public Flow<List<WorkoutSetEntity>> observeSetsForExercise(final String exerciseName) {
    final String _sql = "SELECT * FROM workout_set WHERE exerciseName = ? ORDER BY timestampMillis ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, exerciseName);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"workout_set"}, new Callable<List<WorkoutSetEntity>>() {
      @Override
      @NonNull
      public List<WorkoutSetEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSetId = CursorUtil.getColumnIndexOrThrow(_cursor, "setId");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfExerciseName = CursorUtil.getColumnIndexOrThrow(_cursor, "exerciseName");
          final int _cursorIndexOfSetType = CursorUtil.getColumnIndexOrThrow(_cursor, "setType");
          final int _cursorIndexOfSetOrder = CursorUtil.getColumnIndexOrThrow(_cursor, "setOrder");
          final int _cursorIndexOfWeightKg = CursorUtil.getColumnIndexOrThrow(_cursor, "weightKg");
          final int _cursorIndexOfReps = CursorUtil.getColumnIndexOrThrow(_cursor, "reps");
          final int _cursorIndexOfE1rm = CursorUtil.getColumnIndexOrThrow(_cursor, "e1rm");
          final int _cursorIndexOfTimestampMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "timestampMillis");
          final List<WorkoutSetEntity> _result = new ArrayList<WorkoutSetEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final WorkoutSetEntity _item;
            final long _tmpSetId;
            _tmpSetId = _cursor.getLong(_cursorIndexOfSetId);
            final long _tmpSessionId;
            _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            final String _tmpExerciseName;
            _tmpExerciseName = _cursor.getString(_cursorIndexOfExerciseName);
            final String _tmpSetType;
            _tmpSetType = _cursor.getString(_cursorIndexOfSetType);
            final int _tmpSetOrder;
            _tmpSetOrder = _cursor.getInt(_cursorIndexOfSetOrder);
            final double _tmpWeightKg;
            _tmpWeightKg = _cursor.getDouble(_cursorIndexOfWeightKg);
            final int _tmpReps;
            _tmpReps = _cursor.getInt(_cursorIndexOfReps);
            final double _tmpE1rm;
            _tmpE1rm = _cursor.getDouble(_cursorIndexOfE1rm);
            final long _tmpTimestampMillis;
            _tmpTimestampMillis = _cursor.getLong(_cursorIndexOfTimestampMillis);
            _item = new WorkoutSetEntity(_tmpSetId,_tmpSessionId,_tmpExerciseName,_tmpSetType,_tmpSetOrder,_tmpWeightKg,_tmpReps,_tmpE1rm,_tmpTimestampMillis);
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
  public Flow<List<String>> observeLoggedExerciseNames() {
    final String _sql = "SELECT DISTINCT exerciseName FROM workout_set ORDER BY exerciseName ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"workout_set"}, new Callable<List<String>>() {
      @Override
      @NonNull
      public List<String> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final List<String> _result = new ArrayList<String>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final String _item;
            _item = _cursor.getString(0);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
