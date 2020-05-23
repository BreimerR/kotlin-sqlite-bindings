import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.io.File

class JniWriter(
    val pairs: List<FunctionPair>
) {
    fun write(file: File) {
        val spec = FileSpec.builder("com.birbit.sqlite3.internal", "GeneratedJni").apply {
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
        spec.writeTo(file)
    }

    fun generate(pair: FunctionPair) = FunSpec.builder(pair.actualFun.name).apply {
        addAnnotation(AnnotationSpec.builder(ClassNames.CNAME).apply {
            this.addMember("%S", pair.jniSignature)
        }.build())
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
                "p${index}",
                type.nativeClass
            ).build()
            params.add(pair.actualFun.paramTypes[index] to param)
            addParameter(
                param
            )
        }
        // TODO add only for entry methods
        addStatement("initPlatform()")
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
            addCode(pair.actualFun.returnType.convertToJni(envParam, RETURN_VALUE_NAME, localResultName))
            addStatement("return %L", localResultName)
        } else {
            addStatement("return %L", RETURN_VALUE_NAME)
        }
        returns(pair.nativeFun.returnType.nativeClass)
    }.build()

    companion object {
        val RETURN_VALUE_NAME = "callResult"
    }
}