/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.server.backup;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Interface and methods used by the asynchronous-with-timeout backup/restore operations. */
public interface BackupRestoreTask {

    // Execute one tick of whatever state machine the task implements
    void execute();

    // An operation that wanted a callback has completed
    void operationComplete(long result);

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        CancellationReason.TIMEOUT,
        CancellationReason.AGENT_DISCONNECTED,
        CancellationReason.EXTERNAL,
        CancellationReason.SCHEDULED_JOB_STOPPED,
    })
    @interface CancellationReason {
        // The task timed out.
        int TIMEOUT = 0;
        // The agent went away before the task was able to finish (e.g. due to an app crash).
        int AGENT_DISCONNECTED = 1;
        // An external caller cancelled the operation (e.g. via BackupManager#cancelBackups).
        int EXTERNAL = 2;
        // The job scheduler has stopped an ongoing scheduled backup pass.
        int SCHEDULED_JOB_STOPPED = 3;
    }

    /** The task is cancelled for the given {@link CancellationReason}. */
    void handleCancel(@CancellationReason int cancellationReason);
}
