// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.extensions.events;

import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.RevisionCreatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.patch.PatchListObjectTooLargeException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RevisionCreated {
  private static final Logger log = LoggerFactory.getLogger(RevisionCreated.class);

  public static final RevisionCreated DISABLED =
      new RevisionCreated() {
        @Override
        public void fire(
            Change change,
            PatchSet patchSet,
            Account uploader,
            Timestamp when,
            NotifyHandling notify) {}
      };

  private final DynamicSet<RevisionCreatedListener> listeners;
  private final EventUtil util;

  @Inject
  RevisionCreated(DynamicSet<RevisionCreatedListener> listeners, EventUtil util) {
    this.listeners = listeners;
    this.util = util;
  }

  private RevisionCreated() {
    this.listeners = null;
    this.util = null;
  }

  public void fire(
      Change change, PatchSet patchSet, Account uploader, Timestamp when, NotifyHandling notify) {
    if (!listeners.iterator().hasNext()) {
      return;
    }
    try {
      Event event =
          new Event(
              util.changeInfo(change),
              util.revisionInfo(change.getProject(), patchSet),
              util.accountInfo(uploader),
              when,
              notify);
      for (RevisionCreatedListener l : listeners) {
        try {
          l.onRevisionCreated(event);
        } catch (Exception e) {
          util.logEventListenerError(this, l, e);
        }
      }
    } catch (PatchListObjectTooLargeException e) {
      log.warn("Couldn't fire event: " + e.getMessage());
    } catch (PatchListNotAvailableException
        | GpgException
        | IOException
        | OrmException
        | PermissionBackendException e) {
      log.error("Couldn't fire event", e);
    }
  }

  private static class Event extends AbstractRevisionEvent
      implements RevisionCreatedListener.Event {

    Event(
        ChangeInfo change,
        RevisionInfo revision,
        AccountInfo uploader,
        Timestamp when,
        NotifyHandling notify) {
      super(change, revision, uploader, when, notify);
    }
  }
}
