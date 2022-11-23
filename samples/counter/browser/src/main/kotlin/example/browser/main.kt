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
package example.browser

import app.cash.redwood.compose.RedwoodComposition
import app.cash.redwood.compose.WindowAnimationFrameClock
import app.cash.redwood.widget.HTMLElementChildren
import example.browser.sunspot.HtmlSunspotNodeFactory
import example.shared.Counter
import kotlinx.browser.document
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.plus
import org.w3c.dom.HTMLElement

fun main() {
  val content = document.getElementById("content")!! as HTMLElement

  @OptIn(DelicateCoroutinesApi::class)
  val composition = RedwoodComposition(
    scope = GlobalScope + WindowAnimationFrameClock,
    container = HTMLElementChildren(content),
    factory = HtmlSunspotNodeFactory(document),
  )
  composition.setContent {
    Counter()
  }
}
