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
package app.cash.redwood.tooling.schema

import app.cash.redwood.layout.RedwoodLayout
import app.cash.redwood.layout.Row
import app.cash.redwood.schema.Children
import app.cash.redwood.schema.Modifier
import app.cash.redwood.schema.Property
import app.cash.redwood.schema.Schema
import app.cash.redwood.schema.Schema.Dependency
import app.cash.redwood.schema.Widget
import app.cash.redwood.tooling.schema.FqType.Variance.Out
import app.cash.redwood.tooling.schema.ProtocolWidget.ProtocolProperty
import app.cash.redwood.tooling.schema.Widget.Children as ChildrenTrait
import app.cash.redwood.tooling.schema.Widget.Event
import app.cash.redwood.tooling.schema.Widget.Property as PropertyTrait
import assertk.all
import assertk.assertAll
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.containsMatch
import assertk.assertions.hasMessage
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.message
import assertk.assertions.prop
import com.example.redwood.testapp.TestSchema
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import java.io.File
import kotlin.DeprecationLevel.HIDDEN
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class SchemaParserTest(
  @TestParameter private val parser: SchemaParser,
) {
  object TestScope

  interface NonAnnotationSchema

  @Test fun nonAnnotatedSchemaThrows() {
    assertFailure { parser.parse(NonAnnotationSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "Schema app.cash.redwood.tooling.schema.SchemaParserTest.NonAnnotationSchema missing @Schema annotation",
      )
  }

  @Schema(
    [
      NonAnnotatedMember::class,
    ],
  )
  interface NonAnnotatedWidgetSchema
  data class NonAnnotatedMember(
    @Property(1) val name: String,
  )

  @Test fun nonAnnotatedWidgetThrows() {
    assertFailure { parser.parse(NonAnnotatedWidgetSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "app.cash.redwood.tooling.schema.SchemaParserTest.NonAnnotatedMember must be annotated with either @Widget or @Modifier",
      )
  }

  @Schema(
    [
      DoubleAnnotatedWidget::class,
    ],
  )
  interface DoubleAnnotatedWidgetSchema

  @Widget(1)
  @Modifier(1, TestScope::class)
  data class DoubleAnnotatedWidget(
    @Property(1) val name: String,
  )

  @Test fun doubleAnnotatedWidgetThrows() {
    assertFailure { parser.parse(DoubleAnnotatedWidgetSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "app.cash.redwood.tooling.schema.SchemaParserTest.DoubleAnnotatedWidget must be annotated with either @Widget or @Modifier",
      )
  }

  @Schema(
    [
      DuplicateWidgetTagA::class,
      NonDuplicateWidgetTag::class,
      DuplicateWidgetTagB::class,
    ],
  )
  interface DuplicateWidgetTagSchema

  @Widget(1)
  data class DuplicateWidgetTagA(
    @Property(1) val name: String,
  )

  @Widget(2)
  data class NonDuplicateWidgetTag(
    @Property(1) val name: String,
  )

  @Widget(1)
  data class DuplicateWidgetTagB(
    @Property(1) val name: String,
  )

  @Test fun duplicateWidgetTagThrows() {
    assertFailure { parser.parse(DuplicateWidgetTagSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        """
        |Schema @Widget tags must be unique
        |
        |- @Widget(1): app.cash.redwood.tooling.schema.SchemaParserTest.DuplicateWidgetTagA, app.cash.redwood.tooling.schema.SchemaParserTest.DuplicateWidgetTagB
        """.trimMargin(),
      )
  }

  @Schema(
    [
      DuplicateModifierTagA::class,
      NonDuplicateModifierTag::class,
      DuplicateModifierTagB::class,
    ],
  )
  interface DuplicateModifierTagSchema

  @Modifier(1, TestScope::class)
  data class DuplicateModifierTagA(
    val name: String,
  )

  @Modifier(2, TestScope::class)
  data class NonDuplicateModifierTag(
    val name: String,
  )

  @Modifier(1, TestScope::class)
  data class DuplicateModifierTagB(
    val name: String,
  )

  @Test fun duplicateModifierTagThrows() {
    assertFailure { parser.parse(DuplicateModifierTagSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        """
        |Schema @Modifier tags must be unique
        |
        |- @Modifier(1): app.cash.redwood.tooling.schema.SchemaParserTest.DuplicateModifierTagA, app.cash.redwood.tooling.schema.SchemaParserTest.DuplicateModifierTagB
        """.trimMargin(),
      )
  }

  @Schema(
    [
      RepeatedWidget::class,
      RepeatedWidget::class,
    ],
  )
  interface RepeatedWidgetTypeSchema

  @Widget(1)
  data class RepeatedWidget(
    @Property(1) val name: String,
  )

  @Test fun repeatedWidgetTypeThrows() {
    assertFailure { parser.parse(RepeatedWidgetTypeSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        """
        |Schema contains repeated member
        |
        |- app.cash.redwood.tooling.schema.SchemaParserTest.RepeatedWidget
        """.trimMargin(),
      )
  }

  @Schema(
    [
      DuplicatePropertyTagWidget::class,
    ],
  )
  interface DuplicatePropertyTagSchema

  @Widget(1)
  data class DuplicatePropertyTagWidget(
    @Property(1) val name: String,
    @Property(2) val age: Int,
    @Property(1) val nickname: String,
  )

  @Test fun duplicatePropertyTagThrows() {
    assertFailure { parser.parse(DuplicatePropertyTagSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        """
        |app.cash.redwood.tooling.schema.SchemaParserTest.DuplicatePropertyTagWidget's @Property tags must be unique
        |
        |- @Property(1): name, nickname
        """.trimMargin(),
      )
  }

  @Schema(
    [
      DuplicateChildrenTagWidget::class,
    ],
  )
  interface DuplicateChildrenTagSchema

  @Widget(1)
  data class DuplicateChildrenTagWidget(
    @Children(1) val childrenA: () -> Unit,
    @Property(1) val name: String,
    @Children(1) val childrenB: () -> Unit,
  )

  @Test fun duplicateChildrenTagThrows() {
    assertFailure { parser.parse(DuplicateChildrenTagSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        """
        |app.cash.redwood.tooling.schema.SchemaParserTest.DuplicateChildrenTagWidget's @Children tags must be unique
        |
        |- @Children(1): childrenA, childrenB
        """.trimMargin(),
      )
  }

  @Schema(
    [
      UnannotatedPrimaryParameterWidget::class,
    ],
  )
  interface UnannotatedPrimaryParameterSchema

  @Widget(1)
  data class UnannotatedPrimaryParameterWidget(
    @Property(1) val name: String,
    @Children(1) val children: () -> Unit,
    val unannotated: String,
  )

  @Test fun unannotatedPrimaryParameterThrows() {
    assertFailure { parser.parse(UnannotatedPrimaryParameterSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "Unannotated parameter \"unannotated\" on app.cash.redwood.tooling.schema.SchemaParserTest.UnannotatedPrimaryParameterWidget",
      )
  }

  @Schema(
    [
      NonDataClassWidget::class,
    ],
  )
  interface NonDataClassWidgetSchema

  @Widget(1)
  class NonDataClassWidget(
    @Property(1) val name: String,
  )

  @Test fun nonDataClassWidgetThrows() {
    assertFailure { parser.parse(NonDataClassWidgetSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "@Widget app.cash.redwood.tooling.schema.SchemaParserTest.NonDataClassWidget must be 'data' class or object",
      )
  }

  @Schema(
    [
      NonDataClassModifier::class,
    ],
  )
  interface NonDataClassModifierSchema

  @Modifier(1, TestScope::class)
  class NonDataClassModifier(
    val name: String,
  )

  @Test fun nonDataClassModifierThrows() {
    assertFailure { parser.parse(NonDataClassModifierSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "@Modifier app.cash.redwood.tooling.schema.SchemaParserTest.NonDataClassModifier must be 'data' class or object",
      )
  }

  @Schema(
    [
      PropertyTypesWidget::class,
    ],
  )
  interface PropertyTypesSchema

  @Widget(1)
  @Suppress("ArrayInDataClass")
  data class PropertyTypesWidget(
    @Property(1) val string: String,
    @Property(2) val nullableString: String?,
    @Property(3) val listOfString: List<String>,
    @Property(4) val listOfNullableString: List<String?>,
    @Property(5) val listOfStar: List<*>,
    @Property(6) val arrayOfString: Array<String>,
    @Property(7) val arrayOfNullableString: Array<String?>,
    @Property(8) val arrayOfOutString: Array<out String>,
    @Property(9) val nested: Map.Entry<String, String>,
  )

  @Test fun propertyTypes() {
    val schema = parser.parse(PropertyTypesSchema::class).schema
    val widget = schema.widgets.single()

    val string = widget.traits.single { it.name == "string" } as ProtocolProperty
    assertThat(string.type).isEqualTo(String::class.toFqType())
    val nullableString = widget.traits.single { it.name == "nullableString" } as ProtocolProperty
    assertThat(nullableString.type).isEqualTo(String::class.toFqType(nullable = true))
    val listOfString = widget.traits.single { it.name == "listOfString" } as ProtocolProperty
    assertThat(listOfString.type).isEqualTo(List::class.toFqType(String::class.toFqType()))
    val listOfNullableString = widget.traits.single { it.name == "listOfNullableString" } as ProtocolProperty
    assertThat(listOfNullableString.type).isEqualTo(List::class.toFqType(String::class.toFqType(nullable = true)))
    val listOfStar = widget.traits.single { it.name == "listOfStar" } as ProtocolProperty
    assertThat(listOfStar.type).isEqualTo(List::class.toFqType(FqType.Star))
    val arrayOfString = widget.traits.single { it.name == "arrayOfString" } as ProtocolProperty
    assertThat(arrayOfString.type).isEqualTo(Array::class.toFqType(String::class.toFqType()))
    val arrayOfNullableString = widget.traits.single { it.name == "arrayOfNullableString" } as ProtocolProperty
    assertThat(arrayOfNullableString.type).isEqualTo(Array::class.toFqType(String::class.toFqType(nullable = true)))
    val arrayOfOutString = widget.traits.single { it.name == "arrayOfOutString" } as ProtocolProperty
    assertThat(arrayOfOutString.type).isEqualTo(Array::class.toFqType(String::class.toFqType(variance = Out)))
    val nested = widget.traits.single { it.name == "nested" } as ProtocolProperty
    assertThat(nested.type).isEqualTo(Map.Entry::class.toFqType(String::class.toFqType(), String::class.toFqType()))
  }

  @Schema(
    [
      ModifierTypes::class,
    ],
  )
  interface ModifierTypesSchema

  @Modifier(1)
  @Suppress("ArrayInDataClass")
  data class ModifierTypes(
    val string: String,
    val nullableString: String?,
    val listOfString: List<String>,
    val listOfNullableString: List<String?>,
    val listOfStar: List<*>,
    val arrayOfString: Array<String>,
    val arrayOfNullableString: Array<String?>,
    val arrayOfOutString: Array<out String>,
    val nested: Map.Entry<String, String>,
  )

  @Test fun modifierTypes() {
    val schema = parser.parse(ModifierTypesSchema::class).schema
    val modifier = schema.modifiers.single()

    val string = modifier.properties.single { it.name == "string" }
    assertThat(string.type).isEqualTo(String::class.toFqType())
    val nullableString = modifier.properties.single { it.name == "nullableString" }
    assertThat(nullableString.type).isEqualTo(String::class.toFqType(nullable = true))
    val listOfString = modifier.properties.single { it.name == "listOfString" }
    assertThat(listOfString.type).isEqualTo(List::class.toFqType(String::class.toFqType()))
    val listOfNullableString = modifier.properties.single { it.name == "listOfNullableString" }
    assertThat(listOfNullableString.type).isEqualTo(List::class.toFqType(String::class.toFqType(nullable = true)))
    val listOfStar = modifier.properties.single { it.name == "listOfStar" }
    assertThat(listOfStar.type).isEqualTo(List::class.toFqType(FqType.Star))
    val arrayOfString = modifier.properties.single { it.name == "arrayOfString" }
    assertThat(arrayOfString.type).isEqualTo(Array::class.toFqType(String::class.toFqType()))
    val arrayOfNullableString = modifier.properties.single { it.name == "arrayOfNullableString" }
    assertThat(arrayOfNullableString.type).isEqualTo(Array::class.toFqType(String::class.toFqType(nullable = true)))
    val arrayOfOutString = modifier.properties.single { it.name == "arrayOfOutString" }
    assertThat(arrayOfOutString.type).isEqualTo(Array::class.toFqType(String::class.toFqType(variance = Out)))
    val nested = modifier.properties.single { it.name == "nested" }
    assertThat(nested.type).isEqualTo(Map.Entry::class.toFqType(String::class.toFqType(), String::class.toFqType()))
  }

  @Schema(
    [
      InvalidChildrenTypeWidget::class,
    ],
  )
  interface InvalidChildrenTypeSchema

  @Widget(1)
  data class InvalidChildrenTypeWidget(
    @Children(1) val children: String,
  )

  @Test fun invalidChildrenTypeThrows() {
    assertFailure { parser.parse(InvalidChildrenTypeSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "@Children app.cash.redwood.tooling.schema.SchemaParserTest.InvalidChildrenTypeWidget#children must be of type '() -> Unit'",
      )
  }

  @Schema(
    [
      InvalidChildrenLambdaReturnTypeWidget::class,
    ],
  )
  interface InvalidChildrenLambdaReturnTypeSchema

  @Widget(1)
  data class InvalidChildrenLambdaReturnTypeWidget(
    @Children(1) val children: () -> String,
  )

  @Test fun invalidChildrenLambdaReturnTypeThrows() {
    assertFailure { parser.parse(InvalidChildrenLambdaReturnTypeSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "@Children app.cash.redwood.tooling.schema.SchemaParserTest.InvalidChildrenLambdaReturnTypeWidget#children must be of type '() -> Unit'",
      )
  }

  @Schema(
    [
      ChildrenArgumentsInvalidWidget::class,
    ],
  )
  interface ChildrenArgumentsInvalidSchema

  @Widget(1)
  data class ChildrenArgumentsInvalidWidget(
    @Children(1) val children: (String) -> Unit,
  )

  @Test fun childrenArgumentsInvalid() {
    assertFailure { parser.parse(ChildrenArgumentsInvalidSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "@Children app.cash.redwood.tooling.schema.SchemaParserTest.ChildrenArgumentsInvalidWidget#children lambda type must not have any arguments. " +
          "Found: [kotlin.String]",
      )
  }

  @Schema(
    [
      ScopedChildrenArgumentsInvalidWidget::class,
    ],
  )
  interface ScopedChildrenArgumentsInvalidSchema

  @Widget(1)
  data class ScopedChildrenArgumentsInvalidWidget(
    @Children(1) val children: String.(Int) -> Unit,
  )

  @Test fun scopedChildrenArgumentsInvalid() {
    assertFailure { parser.parse(ScopedChildrenArgumentsInvalidSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "@Children app.cash.redwood.tooling.schema.SchemaParserTest.ScopedChildrenArgumentsInvalidWidget#children lambda type must not have any arguments. " +
          "Found: [kotlin.Int]",
      )
  }

  @Schema(
    [
      ScopedChildrenGenericInvalidWidget::class,
    ],
  )
  interface ScopedChildrenGenericInvalidSchema

  @Widget(1)
  data class ScopedChildrenGenericInvalidWidget(
    @Children(1) val children: List<Int>.() -> Unit,
  )

  @Test fun scopedChildrenGenericInvalid() {
    assertFailure { parser.parse(ScopedChildrenGenericInvalidSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .message()
      .isNotNull()
      .all {
        contains("@Children app.cash.redwood.tooling.schema.SchemaParserTest.ScopedChildrenGenericInvalidWidget#children lambda receiver can only be a non-null class.")
        containsMatch(Regex("""Found: (kotlin\.collections\.)?List<(kotlin\.)?Int>"""))
      }
  }

  @Schema(
    [
      ScopedChildrenNullableInvalidWidget::class,
    ],
  )
  interface ScopedChildrenNullableInvalidSchema

  @Widget(1)
  data class ScopedChildrenNullableInvalidWidget(
    @Children(1) val children: String?.() -> Unit,
  )

  @Test fun scopedChildrenNullableInvalid() {
    assertFailure { parser.parse(ScopedChildrenNullableInvalidSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .message()
      .isNotNull()
      .all {
        contains("@Children app.cash.redwood.tooling.schema.SchemaParserTest.ScopedChildrenNullableInvalidWidget#children lambda receiver can only be a non-null class.")
        containsMatch(Regex("""Found: (kotlin\.)?String?"""))
      }
  }

  @Schema(
    [
      ScopedChildrenTypeParameterInvalidWidget::class,
    ],
  )
  interface ScopedChildrenTypeParameterInvalidSchema

  @Widget(1)
  data class ScopedChildrenTypeParameterInvalidWidget<T>(
    @Children(1) val children: T.() -> Unit,
  )

  @Test fun scopedChildrenTypeParameterInvalid() {
    assertFailure { parser.parse(ScopedChildrenTypeParameterInvalidSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "@Children app.cash.redwood.tooling.schema.SchemaParserTest.ScopedChildrenTypeParameterInvalidWidget#children lambda receiver can only be a non-null class. " +
          "Found: T",
      )
  }

  @Schema(
    [
      EventTypeWidget::class,
    ],
  )
  interface EventTypeSchema

  @Widget(1)
  data class EventTypeWidget(
    @Property(1) val requiredEvent: () -> Unit,
    @Property(2) val optionalEvent: (() -> Unit)?,
  )

  @Test fun eventTypes() {
    val schema = parser.parse(EventTypeSchema::class).schema
    val widget = schema.widgets.single()
    assertThat(widget.traits.single { it.name == "requiredEvent" })
      .isInstanceOf<Event>()
      .all {
        prop(Event::parameterTypes).isEmpty()
        prop(Event::isNullable).isFalse()
      }
    assertThat(widget.traits.single { it.name == "optionalEvent" })
      .isInstanceOf<Event>()
      .all {
        prop(Event::parameterTypes).isEmpty()
        prop(Event::isNullable).isTrue()
      }
  }

  @Schema(
    [
      EventArgumentsWidget::class,
    ],
  )
  interface EventArgumentsSchema

  @Widget(1)
  data class EventArgumentsWidget(
    @Property(1) val noArguments: () -> Unit,
    @Property(2) val oneArgument: (String) -> Unit,
    @Property(3) val oneArgumentOptional: ((String) -> Unit)?,
    @Property(4) val manyArguments: (String, Boolean?, Long) -> Unit,
    @Property(5) val manyArgumentsOptional: ((String, Boolean?, Long) -> Unit)?,
  )

  @Test fun eventArguments() {
    val schema = parser.parse(EventArgumentsSchema::class).schema
    val widget = schema.widgets.single()

    val noArguments = widget.traits.single { it.name == "noArguments" } as Event
    assertThat(noArguments.parameterTypes).isEmpty()
    val oneArgument = widget.traits.single { it.name == "oneArgument" } as Event
    assertThat(oneArgument.parameterTypes).containsExactly(String::class.toFqType())
    val oneArgumentOptional = widget.traits.single { it.name == "oneArgumentOptional" } as Event
    assertThat(oneArgumentOptional.parameterTypes).containsExactly(String::class.toFqType())
    val manyArguments = widget.traits.single { it.name == "manyArguments" } as Event
    assertThat(manyArguments.parameterTypes).containsExactly(String::class.toFqType(), Boolean::class.toFqType(nullable = true), Long::class.toFqType())
    val manyArgumentOptional = widget.traits.single { it.name == "manyArgumentsOptional" } as Event
    assertThat(manyArgumentOptional.parameterTypes).containsExactly(String::class.toFqType(), Boolean::class.toFqType(nullable = true), Long::class.toFqType())
  }

  @Schema(
    [
      ObjectWidget::class,
    ],
  )
  interface ObjectWidgetSchema

  @Widget(1)
  object ObjectWidget

  @Test fun objectWidget() {
    val schema = parser.parse(ObjectWidgetSchema::class).schema
    val widget = schema.widgets.single()
    assertThat(widget.traits).isEmpty()
  }

  @Schema(
    [
      DataObjectWidget::class,
    ],
  )
  interface DataObjectWidgetSchema

  @Widget(1)
  data object DataObjectWidget

  @Test fun dataObjectWidget() {
    val schema = parser.parse(DataObjectWidgetSchema::class).schema
    val widget = schema.widgets.single()
    assertThat(widget.traits).isEmpty()
  }

  @Schema(
    [
      ObjectModifier::class,
    ],
  )
  interface ObjectModifierSchema

  @Modifier(1)
  object ObjectModifier

  @Test fun objectModifier() {
    val schema = parser.parse(ObjectModifierSchema::class).schema
    val modifier = schema.modifiers.single()
    assertThat(modifier.properties).isEmpty()
  }

  @Schema(
    [
      DataObjectModifier::class,
    ],
  )
  interface DataObjectModifierSchema

  @Modifier(1)
  data object DataObjectModifier

  @Test fun dataObjectModifier() {
    val schema = parser.parse(DataObjectModifierSchema::class).schema
    val modifier = schema.modifiers.single()
    assertThat(modifier.properties).isEmpty()
  }

  @Schema(
    [
      OneMillionWidget::class,
    ],
  )
  interface OneMillionWidgetSchema

  @Widget(1_000_000)
  object OneMillionWidget

  @Test fun widgetTagOneMillionThrows() {
    assertFailure { parser.parse(OneMillionWidgetSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "@Widget app.cash.redwood.tooling.schema.SchemaParserTest.OneMillionWidget " +
          "tag must be in range [1, 1000000): 1000000",
      )
  }

  @Schema(
    [
      ZeroWidget::class,
    ],
  )
  interface ZeroWidgetSchema

  @Widget(0)
  object ZeroWidget

  @Test fun widgetTagZeroThrows() {
    assertFailure { parser.parse(ZeroWidgetSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "@Widget app.cash.redwood.tooling.schema.SchemaParserTest.ZeroWidget " +
          "tag must be in range [1, 1000000): 0",
      )
  }

  @Schema(
    [
      OneMillionModifier::class,
    ],
  )
  interface OneMillionModifierSchema

  @Modifier(1_000_000, TestScope::class)
  data class OneMillionModifier(
    val value: Int,
  )

  @Test fun modifierTagOneMillionThrows() {
    assertFailure { parser.parse(OneMillionModifierSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "@Modifier app.cash.redwood.tooling.schema.SchemaParserTest.OneMillionModifier " +
          "tag must be in range [1, 1000000): 1000000",
      )
  }

  @Schema(
    [
      ZeroModifier::class,
    ],
  )
  interface ZeroModifierSchema

  @Modifier(0, TestScope::class)
  data class ZeroModifier(
    val value: Int,
  )

  @Test fun modifierTagZeroThrows() {
    assertFailure { parser.parse(ZeroModifierSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "@Modifier app.cash.redwood.tooling.schema.SchemaParserTest.ZeroModifier " +
          "tag must be in range [1, 1000000): 0",
      )
  }

  @Schema(
    [
      Reuse::class,
    ],
  )
  interface SpecialModifierSchema

  @Modifier(-4_543_827)
  data object Reuse

  @Test fun specialModifiersAllowed() {
    val schema = parser.parse(SpecialModifierSchema::class).schema

    val modifier = schema.modifiers.single()
    assertThat(modifier.tag).isEqualTo(-4_543_827)
  }

  @Schema([SomeWidget::class, SomeModifier::class])
  interface SchemaTag

  @Widget(1)
  data class SomeWidget(
    @Property(1) val value: Int,
    @Children(1) val children: () -> Unit,
  )

  @Modifier(1, TestScope::class)
  data class SomeModifier(
    val value: Int,
  )

  @Test fun schemaTagDefault() {
    val schema = parser.parse(SchemaTag::class).schema

    val widget = schema.widgets.single()
    assertThat(widget.tag).isEqualTo(1)
    assertThat(widget.traits[0].tag).isEqualTo(1)
    assertThat(widget.traits[1].tag).isEqualTo(1)

    val modifier = schema.modifiers.single()
    assertThat(modifier.tag).isEqualTo(1)
  }

  @Schema(
    members = [],
    reservedWidgets = [1, 2, 3, 1, 3, 1],
  )
  interface SchemaDuplicateReservedWidgets

  @Test fun schemaDuplicateReservedWidgetsFails() {
    assertFailure {
      parser.parse(SchemaDuplicateReservedWidgets::class)
    }.isInstanceOf<IllegalArgumentException>()
      .hasMessage("Schema reserved widgets contains duplicates [1, 3]")
  }

  @Schema(
    members = [],
    reservedModifiers = [1, 2, 3, 1, 3, 1],
  )
  interface SchemaDuplicateReservedModifiers

  @Test fun schemaDuplicateReservedModifiersFails() {
    assertFailure {
      parser.parse(SchemaDuplicateReservedModifiers::class)
    }.isInstanceOf<IllegalArgumentException>()
      .hasMessage("Schema reserved modifiers contains duplicates [1, 3]")
  }

  @Schema(
    members = [ReservedWidget::class],
    reservedWidgets = [1, 2, 3],
  )
  interface SchemaReservedWidgetCollision

  @Widget(2)
  object ReservedWidget

  @Test fun schemaReservedWidgetCollision() {
    assertFailure {
      parser.parse(SchemaReservedWidgetCollision::class)
    }.isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        """
        |Schema @Widget tags must not be included in reserved set [1, 2, 3]
        |
        |- @Widget(2) app.cash.redwood.tooling.schema.SchemaParserTest.ReservedWidget
        """.trimMargin(),
      )
  }

  @Schema(
    members = [ReservedModifier::class],
    reservedModifiers = [1, 2, 3],
  )
  interface SchemaReservedModifierCollision

  @Modifier(2, TestScope::class)
  object ReservedModifier

  @Test fun schemaReservedModifierCollision() {
    assertFailure {
      parser.parse(SchemaReservedModifierCollision::class)
    }.isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        """
        |Schema @Modifier tags must not be included in reserved set [1, 2, 3]
        |
        |- @Modifier(2, …) app.cash.redwood.tooling.schema.SchemaParserTest.ReservedModifier
        """.trimMargin(),
      )
  }

  @Schema(
    members = [ReservedModifier::class],
    reservedWidgets = [1, 2, 3],
  )
  interface SchemaReservedWidgetDoesNotApplyToModifier

  @Test fun schemaReservedWidgetDoesNotApplyToModifier() {
    val schema = parser.parse(SchemaReservedWidgetDoesNotApplyToModifier::class).schema
    assertThat(schema.widgets).hasSize(0)
    assertThat(schema.modifiers).hasSize(1)
  }

  @Schema(
    members = [ReservedWidget::class],
    reservedModifiers = [1, 2, 3],
  )
  interface SchemaReservedModifierDoesNotApplyToWidget

  @Test fun schemaReservedModifierDoesNotApplyToWidget() {
    val schema = parser.parse(SchemaReservedModifierDoesNotApplyToWidget::class).schema
    assertThat(schema.widgets).hasSize(1)
    assertThat(schema.modifiers).hasSize(0)
  }

  @Schema(
    [
      WidgetDuplicateReservedProperties::class,
    ],
  )
  interface SchemaDuplicateReservedProperties

  @Widget(1, reservedProperties = [1, 2, 3, 1, 3, 1])
  object WidgetDuplicateReservedProperties

  @Test fun widgetDuplicateReservedProperties() {
    assertFailure {
      parser.parse(SchemaDuplicateReservedProperties::class)
    }.isInstanceOf<IllegalArgumentException>()
      .hasMessage("Widget app.cash.redwood.tooling.schema.SchemaParserTest.WidgetDuplicateReservedProperties reserved properties contains duplicates [1, 3]")
  }

  @Schema(
    [
      WidgetDuplicateReservedChildren::class,
    ],
  )
  interface SchemaDuplicateReservedChildren

  @Widget(1, reservedChildren = [1, 2, 3, 1, 3, 1])
  object WidgetDuplicateReservedChildren

  @Test fun widgetDuplicateReservedChildren() {
    assertFailure {
      parser.parse(SchemaDuplicateReservedChildren::class)
    }.isInstanceOf<IllegalArgumentException>()
      .hasMessage("Widget app.cash.redwood.tooling.schema.SchemaParserTest.WidgetDuplicateReservedChildren reserved children contains duplicates [1, 3]")
  }

  @Schema(
    [
      WidgetReservedPropertyCollision::class,
    ],
  )
  interface SchemaReservedPropertyCollision

  @Widget(1, reservedProperties = [2])
  data class WidgetReservedPropertyCollision(
    @Property(1) val text: String,
    @Property(2) val color: Int,
  )

  @Test fun widgetReservedPropertyCollision() {
    assertFailure {
      parser.parse(SchemaReservedPropertyCollision::class)
    }.isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        """
        |Widget app.cash.redwood.tooling.schema.SchemaParserTest.WidgetReservedPropertyCollision @Property tags must not be included in reserved set [2]
        |
        |- @Property(2) color
        """.trimMargin(),
      )
  }

  @Schema(
    [
      WidgetReservedChildrenCollision::class,
    ],
  )
  interface SchemaReservedChildrenCollision

  @Widget(1, reservedChildren = [2])
  data class WidgetReservedChildrenCollision(
    @Children(1) val left: () -> Unit,
    @Children(2) val right: () -> Unit,
  )

  @Test fun widgetReservedChildrenCollision() {
    assertFailure {
      parser.parse(SchemaReservedChildrenCollision::class)
    }.isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        """
        |Widget app.cash.redwood.tooling.schema.SchemaParserTest.WidgetReservedChildrenCollision @Children tags must not be included in reserved set [2]
        |
        |- @Children(2) right
        """.trimMargin(),
      )
  }

  @Schema(
    [
      WidgetReservedPropertyDoesNotApplyToChildren::class,
    ],
  )
  interface SchemaReservedPropertyDoesNotApplyToChildren

  @Widget(1, reservedProperties = [1])
  data class WidgetReservedPropertyDoesNotApplyToChildren(
    @Children(1) val content: () -> Unit,
  )

  @Test fun widgetReservedPropertyDoesNotApplyToChildren() {
    val schema = parser.parse(SchemaReservedPropertyDoesNotApplyToChildren::class).schema
    assertThat(schema.widgets.single().traits).hasSize(1)
  }

  @Schema(
    [
      WidgetReservedChildrenDoesNotApplyToProperty::class,
    ],
  )
  interface SchemaReservedChildrenDoesNotApplyToProperty

  @Widget(1, reservedChildren = [1])
  data class WidgetReservedChildrenDoesNotApplyToProperty(
    @Property(1) val text: String,
  )

  @Test fun widgetReservedChildrenDoesNotApplyToProperty() {
    val schema = parser.parse(SchemaReservedChildrenDoesNotApplyToProperty::class).schema
    assertThat(schema.widgets.single().traits).hasSize(1)
  }

  @Schema(
    members = [],
    dependencies = [
      Dependency(4, RedwoodLayout::class),
    ],
  )
  object SchemaDependencyTagOffsetsMemberTags

  @Test fun schemaTagOffsetsMemberTags() {
    val schema = parser.parse(SchemaDependencyTagOffsetsMemberTags::class)
    val dependency = schema.dependencies.values.single()

    val widget = dependency.widgets.single { it.type.names.last() == "Row" }
    assertThat(widget.tag).isEqualTo(4_000_001)
    val widgetProperty = widget.traits.first { it is PropertyTrait }
    assertThat(widgetProperty.tag).isEqualTo(1)
    val widgetChildren = widget.traits.first { it is ChildrenTrait }
    assertThat(widgetChildren.tag).isEqualTo(1)

    val modifier = dependency.modifiers.single { it.type.names.last() == "Grow" }
    assertThat(modifier.tag).isEqualTo(4_000_001)
  }

  @Schema(
    members = [],
    dependencies = [
      Dependency(2001, RedwoodLayout::class),
    ],
  )
  object SchemaDependencyTagTooHigh

  @Schema(
    members = [],
    dependencies = [
      Dependency(-1, RedwoodLayout::class),
    ],
  )
  object SchemaDependencyTagTooLow

  @Test fun dependencyTagTooHighThrows() {
    assertFailure { parser.parse(SchemaDependencyTagTooHigh::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "app.cash.redwood.layout.RedwoodLayout tag must be 0 for the root or in range (0, 2000] as a dependency: 2001",
      )

    assertFailure { parser.parse(SchemaDependencyTagTooLow::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "app.cash.redwood.layout.RedwoodLayout tag must be 0 for the root or in range (0, 2000] as a dependency: -1",
      )
  }

  @Schema(
    members = [],
    dependencies = [
      Dependency(0, RedwoodLayout::class),
    ],
  )
  object SchemaDependencyTagZero

  @Test fun dependencyTagZeroThrows() {
    assertFailure { parser.parse(SchemaDependencyTagZero::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "Dependency app.cash.redwood.layout.RedwoodLayout tag must not be non-zero",
      )
  }

  @Schema(
    members = [],
    dependencies = [
      Dependency(1, SchemaDuplicateDependencyTagA::class),
      Dependency(1, SchemaDuplicateDependencyTagB::class),
    ],
  )
  object SchemaDuplicateDependencyTag

  @Schema(members = [])
  object SchemaDuplicateDependencyTagA

  @Schema(members = [])
  object SchemaDuplicateDependencyTagB

  @Test fun schemaDuplicateDependencyTagThrows() {
    assertFailure { parser.parse(SchemaDuplicateDependencyTag::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        """
        |Schema dependency tags must be unique
        |
        |- Dependency tag 1: app.cash.redwood.tooling.schema.SchemaParserTest.SchemaDuplicateDependencyTagA, app.cash.redwood.tooling.schema.SchemaParserTest.SchemaDuplicateDependencyTagB
        """.trimMargin(),
      )
  }

  @Schema(
    members = [],
    dependencies = [
      Dependency(1, SchemaDuplicateDependencyTypeOther::class),
      Dependency(2, SchemaDuplicateDependencyTypeOther::class),
    ],
  )
  object SchemaDuplicateDependencyType

  @Schema(members = [])
  object SchemaDuplicateDependencyTypeOther

  @Test fun schemaDuplicateDependencyTypeThrows() {
    assertFailure { parser.parse(SchemaDuplicateDependencyType::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        """
        |Schema contains repeated dependency
        |
        |- app.cash.redwood.tooling.schema.SchemaParserTest.SchemaDuplicateDependencyTypeOther
        """.trimMargin(),
      )
  }

  @Schema(
    members = [],
    dependencies = [
      Dependency(1, TestSchema::class),
    ],
  )
  object SchemaDependencyHasDependency

  @Test fun schemaDependencyHasDependencyThrows() {
    assertFailure { parser.parse(SchemaDependencyHasDependency::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "Schema dependency com.example.redwood.testapp.TestSchema also has its own dependencies. " +
          "For now, only a single level of dependencies is supported.",
      )
  }

  @Schema(
    members = [
      Row::class,
    ],
    dependencies = [
      Dependency(1, RedwoodLayout::class),
    ],
  )
  object SchemaWidgetDuplicateInDependency

  @Test fun schemaWidgetDuplicateInDependencyThrows() {
    assumeTrue(parser != SchemaParser.Fir)

    assertFailure { parser.parse(SchemaWidgetDuplicateInDependency::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        """
        |Schema dependency tree contains duplicated widgets
        |
        |- app.cash.redwood.layout.Row: app.cash.redwood.tooling.schema.SchemaParserTest.SchemaWidgetDuplicateInDependency, app.cash.redwood.layout.RedwoodLayout
        """.trimMargin(),
      )
  }

  @Test fun schemaTypeMustBeInSource() {
    assumeTrue(parser == SchemaParser.Fir)

    assertFailure { parser.parse(RedwoodLayout::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage("Unable to locate schema type app.cash.redwood.layout.RedwoodLayout in compilation unit")
  }

  @Schema(
    members = [
      Row::class,
    ],
  )
  object SchemaMemberNotInCompilationUnit

  @Test fun schemaMembersMustBeInSource() {
    assumeTrue(parser == SchemaParser.Fir)

    assertFailure { parser.parse(SchemaMemberNotInCompilationUnit::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage("Unable to locate schema member app.cash.redwood.layout.Row in compilation unit")
  }

  @Schema(
    [
      SerializationModifier::class,
    ],
  )
  interface SerializationSchema

  @Modifier(1, TestScope::class)
  data class SerializationModifier(
    val no: String,
    val yes: SerializableType,
  )

  @Serializable
  data class SerializableType(
    val whevs: String,
  )

  @Test fun serializableModifierProperties() {
    val schema = parser.parse(SerializationSchema::class).schema
    val modifier = schema.modifiers.single()

    val yesProperty = modifier.properties.single { it.name == "yes" }
    assertThat(yesProperty.isSerializable).isTrue()

    val noProperty = modifier.properties.single { it.name == "no" }
    assertThat(noProperty.isSerializable).isFalse()
  }

  @Schema(
    [
      DeprecationHiddenWidget::class,
    ],
  )
  interface DeprecationHiddenSchema

  @Widget(1)
  data class DeprecationHiddenWidget(
    @Deprecated("", level = HIDDEN)
    val a: String,
  )

  @Test fun deprecationHiddenThrows() {
    assertFailure { parser.parse(DeprecationHiddenSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "Schema deprecation does not support level HIDDEN: " +
          "app.cash.redwood.tooling.schema.SchemaParserTest.DeprecationHiddenWidget.a",
      )
  }

  @Suppress("DEPRECATION")
  @Schema(
    [
      DeprecationReplaceWithWidget::class,
    ],
  )
  interface DeprecationReplaceWithSchema

  @Widget(1)
  @Deprecated("", ReplaceWith("Hello"))
  object DeprecationReplaceWithWidget

  @Test fun deprecationReplaceWithThrows() {
    assertFailure { parser.parse(DeprecationReplaceWithSchema::class) }
      .isInstanceOf<IllegalArgumentException>()
      .hasMessage(
        "Schema deprecation does not support replacements: " +
          "app.cash.redwood.tooling.schema.SchemaParserTest.DeprecationReplaceWithWidget",
      )
  }

  /** Schema single-line documentation. */
  @Schema(
    [
      CommentsWidget::class,
      CommentsModifier::class,
    ],
  )
  interface CommentsSchema

  /**
   * Widget
   * multi-line
   * documentation.
   */
  @Widget(1)
  data class CommentsWidget(
    /**
Property
     weird formatting
     documentation.
     */
    @Property(1) val id: Int,
    /**Children
     *missing
     *spaces documentation.*/
    @Children(1) val content: () -> Unit,
  )

  /**
   * Layout modifier
   * multi-line
   * documentation.
   */
  @Suppress("ktlint:standard:kdoc-wrapping")
  @Modifier(1, TestScope::class)
  data class CommentsModifier(
    /** Property same line documentation. */ val id: Int,
  )

  @Ignore("Missing handling of '/** */' noise")
  @Test
  fun comments() = assertAll {
    assumeTrue(parser == SchemaParser.Fir) // Comments are only available through the AST.

    val schema = parser.parse(CommentsSchema::class).schema
    assertThat(schema.documentation).isEqualTo("Schema single-line documentation.")

    val widget = schema.widgets.single()
    assertThat(widget.documentation).isEqualTo("Widget multi-line documentation.")

    val widgetProperty = widget.traits.single { it is PropertyTrait }
    assertThat(widgetProperty.documentation).isEqualTo("Property weird formatting documentation.")

    val widgetChildren = widget.traits.single { it is ChildrenTrait }
    assertThat(widgetChildren.documentation).isEqualTo("Children missing spaces documentation.")

    val modifier = schema.modifiers.single()
    assertThat(modifier.documentation).isEqualTo("Layout modifier multi-line documentation.")

    val modifierProperty = modifier.properties.single()
    assertThat(modifierProperty.documentation).isEqualTo("Property same line documentation.")
  }

  @Suppress("unused") // Values used by TestParameterInjector.
  enum class SchemaParser {
    Reflection {
      override fun parse(type: KClass<*>): ProtocolSchemaSet {
        return parseProtocolSchemaSet(type)
      }
    },
    Fir {
      override fun parse(type: KClass<*>): ProtocolSchemaSet {
        val jdkHome = System.getProperty("java.home").let(::File)
        val sources = System.getProperty("redwood.internal.sources")
          .split(File.pathSeparator)
          .map(::File)
          .filter(File::exists) // Entries that don't exist produce warnings.
        val classpath = System.getProperty("redwood.internal.classpath")
          .split(File.pathSeparator)
          .map(::File)
          .filter(File::exists) // Entries that don't exist produce warnings.
        return parseProtocolSchema(jdkHome, sources, classpath, type.toFqType())
      }
    },
    ;

    abstract fun parse(type: KClass<*>): ProtocolSchemaSet
  }
}
