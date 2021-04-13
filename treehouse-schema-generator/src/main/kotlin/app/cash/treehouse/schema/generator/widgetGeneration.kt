package app.cash.treehouse.schema.generator

import app.cash.exhaustive.Exhaustive
import app.cash.treehouse.schema.parser.Children
import app.cash.treehouse.schema.parser.Event
import app.cash.treehouse.schema.parser.Property
import app.cash.treehouse.schema.parser.Schema
import app.cash.treehouse.schema.parser.Widget
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier.ABSTRACT
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asTypeName

/*
interface SunspotWidgetFactory<T : Any> : Widget.Factory<T> {
  fun SunspotText(parent: T): SunspotText<T>
  fun SunspotButton(parent: T): SunspotButton<T>

  override fun create(parent: T, kind: Int, id: Long): TreeNode<T> {
    return when (kind) {
      1 -> SunspotText(parent)
      2 -> SunspotButton(parent)
      else -> throw IllegalArgumentException("Unknown kind $kind")
    }
  }
}
*/
internal fun generateWidgetFactory(schema: Schema): FileSpec {
  return FileSpec.builder(schema.displayPackage, schema.getWidgetFactoryType().simpleName)
    .addType(
      TypeSpec.interfaceBuilder(schema.getWidgetFactoryType())
        .addModifiers(PUBLIC)
        .addTypeVariable(typeVariableT)
        .addSuperinterface(factoryOfT)
        .apply {
          for (node in schema.widgets) {
            addFunction(
              FunSpec.builder(node.flatName)
                .addModifiers(PUBLIC, ABSTRACT)
                .addParameter("parent", typeVariableT)
                .returns(schema.widgetType(node).parameterizedBy(typeVariableT))
                .build()
            )
          }
        }
        .addFunction(
          FunSpec.builder("create")
            .addModifiers(PUBLIC, OVERRIDE)
            .addParameter("parent", typeVariableT)
            .addParameter("kind", INT)
            .addParameter("id", LONG)
            .returns(widgetOfT)
            .beginControlFlow("return when (kind)")
            .apply {
              for (node in schema.widgets.sortedBy { it.tag }) {
                addStatement("%L -> %N(parent)", node.tag, node.flatName)
              }
            }
            .addStatement("else -> throw %T(\"Unknown kind \$kind\")", iae)
            .endControlFlow()
            .build()
        )
        .build()
    )
    .build()
}

/*
interface SunspotButton<out T: Any> : SunspotNode<T> {
  fun text(text: String?)
  fun enabled(enabled: Boolean)
  fun onClick(onClick: (() -> Unit)?)

  override fun apply(diff: PropertyDiff, events: (Event) -> Unit) {
    when (val tag = diff.tag) {
      1 -> text(diff.value as String?)
      2 -> enabled(diff.value as Boolean)
      3 -> {
        val onClick: (() -> Unit)? = if (diff.value as Boolean) {
          val event = Event(diff.id, 3, null);
          { events(event) }
        } else {
          null
        }
        onClick(onClick)
      }
      else -> throw IllegalArgumentException("Unknown tag $tag")
    }
  }
}
*/
internal fun generateWidget(schema: Schema, widget: Widget): FileSpec {
  return FileSpec.builder(schema.displayPackage, widget.flatName)
    .addType(
      TypeSpec.interfaceBuilder(widget.flatName)
        .addModifiers(PUBLIC)
        .addTypeVariable(typeVariableT)
        .addSuperinterface(widgetOfT)
        .apply {
          for (trait in widget.traits) {
            when (trait) {
              is Property -> {
                addFunction(
                  FunSpec.builder(trait.name)
                    .addModifiers(PUBLIC, ABSTRACT)
                    .addParameter(trait.name, trait.type.asTypeName())
                    .build()
                )
              }
              is Event -> {
                addFunction(
                  FunSpec.builder(trait.name)
                    .addModifiers(PUBLIC, ABSTRACT)
                    .addParameter(trait.name, eventLambda)
                    .build()
                )
              }
              is Children -> {
                addProperty(
                  PropertySpec.builder(trait.name, childrenOfT)
                    .addModifiers(PUBLIC, ABSTRACT)
                    .build()
                )
              }
            }
          }
          val childrens = widget.traits.filterIsInstance<Children>()
          if (childrens.isNotEmpty()) {
            addFunction(
              FunSpec.builder("children")
                .addModifiers(PUBLIC, OVERRIDE)
                .addParameter("tag", INT)
                .returns(childrenOfT)
                .beginControlFlow("return when (tag)")
                .apply {
                  for (children in childrens) {
                    addStatement("%L -> %N", children.tag, children.name)
                  }
                }
                .addStatement("else -> throw %T(\"Unknown tag \$tag\")", iae)
                .endControlFlow()
                .build()
            )
          }
        }
        .addFunction(
          FunSpec.builder("apply")
            .addModifiers(PUBLIC, OVERRIDE)
            .addParameter("diff", propertyDiff)
            .addParameter("events", eventSink)
            .beginControlFlow("when (val tag = diff.tag)")
            .apply {
              for (trait in widget.traits) {
                @Exhaustive when (trait) {
                  is Property -> {
                    addStatement(
                      "%L -> %N(diff.value as %T)", trait.tag, trait.name,
                      trait.type.asTypeName()
                    )
                  }
                  is Event -> {
                    beginControlFlow("%L ->", trait.tag)
                    beginControlFlow("val %N = if (diff.value as %T)", trait.name, BOOLEAN)
                    addStatement("val event = %T(diff.id, %L, null);", eventType, trait.tag)
                    addStatement("{ events(event) }")
                    nextControlFlow("else")
                    addStatement("null")
                    endControlFlow()
                    addStatement("%1N(%1N)", trait.name)
                    endControlFlow()
                  }
                  is Children -> {}
                }
              }
            }
            .addStatement("else -> throw %T(\"Unknown tag \$tag\")", iae)
            .endControlFlow()
            .build()
        )
        .build()
    )
    .build()
}

private val typeVariableT = TypeVariableName("T", listOf(ANY))
private val widgetOfT = widget.parameterizedBy(typeVariableT)
private val childrenOfT = widgetChildren.parameterizedBy(typeVariableT)
private val factoryOfT = widgetFactory.parameterizedBy(typeVariableT)
