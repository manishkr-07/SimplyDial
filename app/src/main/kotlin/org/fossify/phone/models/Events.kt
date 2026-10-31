package org.fossify.phone.models

sealed class Events {
    data object RefreshCallLog : Events()
    data object CallRecordingStarted : Events()
    data object CallRecordingStopped : Events()
}
