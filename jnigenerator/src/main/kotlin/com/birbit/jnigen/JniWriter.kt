/*
 * Copyright 2020 Google, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.birbit.jnigen

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.io.File
import java.lang.StringBuilder

class JniWriter(
    val copyright: String,
    val pairs: List<FunctionPair>
) {
    fun write(file: File) {
        val spec = FileSpec.builder("com.birbit.sqlite3.internal", "GeneratedJni").apply {
            indent("    ")
            addAnnotation(
                AnnotationSpec.builder(Suppress::class).apply {
                    addMember("%S, %S, %S", "unused", "UNUSED_PARAMETER", "UnnecessaryVariable")
                }.build()
            )
            addComment("Generated by JniWriter, do not edit!")
            pairs.forEach {
                addFunction(generate(it))
            }
        }.build()
        val output = StringBuilder()
        output.appendLine(copyright)
        spec.writeTo(output)
        println("will output to ${file.absolutePath}")
        file.writeText(output.toString(), Charsets.UTF_8)
    }

    fun generate(pair: FunctionPair) = FunSpec.builder(pair.actualFun.name).apply {
        addAnnotation(
            AnnotationSpec.builder(ClassNames.CNAME).apply {
                this.addMember("%S", pair.jniSignature)
            }.build()
        )
        val envParam = ParameterSpec.builder(
            "env",
            ClassNames.CPOINTER.parameterizedBy(ClassNames.JNI_ENV_VAR)
        ).build()
        addParameter(
            envParam
        )
        addParameter(
            ParameterSpec.builder(
                "clazz",
                ClassNames.JCLASS
            ).build()
        )
        val params = mutableListOf<Pair<Type, ParameterSpec>>()
        pair.nativeFun.paramTypes.forEachIndexed { index, type ->
            val param = ParameterSpec.builder(
                "p$index",
                type.nativeClass
            ).build()
            params.add(pair.actualFun.paramTypes[index] to param)
            addParameter(
                param
            )
        }
        // TODO add only for entry methods
        addStatement("initPlatform()")

        /**
         return runWithJniExceptionConversion(env, 0) {
         val localP0 = DbRef.fromJni(p0)
         val localP1 = checkNotNull(p1.toKString(env))
         val callResult = SqliteApi.prepareStmt(localP0, localP1)
         val localCallResult = callResult.toJni()
         localCallResult
         }
         */
        beginControlFlow(
            "return runWithJniExceptionConversion(%N, %L)",
            envParam,
            pair.nativeFun.returnType.defaultValue()
        )
        val argumentNames = params.map {
            if (it.first.hasConvertFromJni()) {
                val localVarName = "local${it.second.name.capitalize()}"
                val convert = it.first.convertFromJni(envParam, it.second, localVarName)
                addCode(convert)
                localVarName
            } else {
                it.second.name
            }
        }
        addStatement(
            "val %L = %T.%L(%L)",
            RETURN_VALUE_NAME,
            ClassNames.SQLITE_API,
            pair.actualFun.name,
            argumentNames.joinToString(", ")
        )
        // now convert back if necessary
        //  addComment("return type: %L , %L", pair.actualFun.returnType, convertToJni == null)
        if (pair.actualFun.returnType.hasConvertToJni()) {
            val localResultName = "local${RETURN_VALUE_NAME.capitalize()}"
            addCode(
                pair.actualFun.returnType.convertToJni(
                    envParam,
                    RETURN_VALUE_NAME, localResultName
                )
            )
            addStatement("%L", localResultName)
        } else {
            addStatement("%L", RETURN_VALUE_NAME)
        }
        returns(pair.nativeFun.returnType.nativeClass)
        endControlFlow()
    }.build()

    companion object {
        val RETURN_VALUE_NAME = "callResult"
    }
}
