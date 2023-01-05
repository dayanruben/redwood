/*
 * Copyright (C) 2021 Square, Inc.
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
package com.example.redwood.counter

import app.cash.redwood.layout.RedwoodLayout
import app.cash.redwood.schema.Default
import app.cash.redwood.schema.Property
import app.cash.redwood.schema.Schema
import app.cash.redwood.schema.Schema.Dependency
import app.cash.redwood.schema.Widget

@Schema(
  [
    Text::class,
    Button::class,
  ],
  dependencies = [
    Dependency(1, RedwoodLayout::class),
  ],
)
interface Schema

@Widget(2)
data class Text(
  @Property(1) val text: String?,
)

@Widget(3)
data class Button(
  @Property(1) val text: String?,
  @Property(2)
  @Default("true")
  val enabled: Boolean = true,
  @Property(3) val onClick: (() -> Unit)? = null,
)
