/*
 * Copyright (C) 2024 Square, Inc.
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
 * limitations under the License.
 */
package app.cash.redwood.protocol.host

import app.cash.redwood.protocol.Event
import app.cash.redwood.protocol.EventSink
import app.cash.redwood.protocol.EventTag
import app.cash.redwood.protocol.Id
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json

/**
 * A version of [Event] whose arguments have not yet been serialized to JSON and is thus
 * cheap to create on the UI thread.
 */
public class UiEvent(
  public val id: Id,
  public val tag: EventTag,
  public val args: Array<Any?>?,
  public val serializationStrategies: Array<SerializationStrategy<Any?>>?,
) {
  /** Serialize [args] into a JSON model using [serializationStrategies] into an [Event]. */
  public fun toProtocol(json: Json): Event {
    return Event(
      id = id,
      tag = tag,
      args = if (args == null) {
        emptyList()
      } else {
        val serializationStrategies = serializationStrategies!!
        List(args.size) { i ->
          json.encodeToJsonElement(serializationStrategies[i], args[i])
        }
      },
    )
  }
}

/** A version of [EventSink] which consumes [UiEvent]s. */
public fun interface UiEventSink {
  public fun sendEvent(uiEvent: UiEvent)
}
