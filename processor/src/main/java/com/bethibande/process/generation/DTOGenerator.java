package com.bethibande.process.generation;

import com.bethibande.process.model.Accessor;
import com.bethibande.process.model.Property;
import com.bethibande.process.model.PropertyType;
import com.palantir.javapoet.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DTOGenerator {

    public static final String METHOD_FROM_PARAMETER = "entity";

    private ProcessingEnvironment environment;

    public DTOGenerator(final ProcessingEnvironment environment) {
        this.environment = environment;
    }

    protected String toDTOPropertyName(final Property property, final GenerationContext ctx) {
        if (property.type() instanceof PropertyType.EntityType && !ctx.shouldExpand(property)) {
            return property.name() + "Id";
        }
        return property.name();
    }

    protected TypeName toDTOPropertyType(final Property property, final GenerationContext ctx) {
        if (property.type() instanceof PropertyType.EntityType entityType) {
            if (!ctx.shouldExpand(property)) {
                return TypeName.get(entityType.getTargetRef()
                        .idProperty()
                        .type()
                        .getType());
            } else {
                return ctx.branch(property).getClassName();
            }
        }

        if (property.type() instanceof PropertyType.EntityCollectionType collectionType) {
            if (!ctx.shouldExpand(property)) {
                final TypeName baseType = TypeName.get(collectionType.getTargetRef()
                        .idProperty()
                        .type()
                        .getType());

                return ParameterizedTypeName.get(ClassName.get(Collection.class), baseType);
            } else {
                return ParameterizedTypeName.get(ClassName.get(Collection.class), ctx.branch(property).getClassName());
            }
        }

        return TypeName.get(property.type().getType());
    }

    protected MethodSpec createConstructor(final GenerationContext ctx) {
        final List<ParameterSpec> parameters = new ArrayList<>();
        for (final Property property : ctx.getProperties()) {
            parameters.add(ParameterSpec.builder(toDTOPropertyType(property, ctx), toDTOPropertyName(property, ctx)).build());
        }

        return MethodSpec.constructorBuilder()
                .addParameters(parameters)
                .build();
    }

    protected Property getActualTargetProperty(final Property property, final GenerationContext ctx) {
        if (property.type() instanceof PropertyType.EntityType entityType) {
            if (!ctx.shouldExpand(property)) {
                return entityType.getTargetRef()
                        .idProperty()
                        .copyWithParent(property);
            }
        }
        if (property.type() instanceof PropertyType.EntityCollectionType entityCollectionType) {
            if (!ctx.shouldExpand(property)) {
                return entityCollectionType.getTargetRef()
                        .idProperty()
                        .copyWithParent(property);
            }
        }
        return property;
    }

    protected MethodSpec createFromMethod(final GenerationContext ctx) {
        final CodeBlock.Builder code = CodeBlock.builder();
        code.addStatement("if ($L == null) return null", METHOD_FROM_PARAMETER);
        code.add("return new $T(\n", ctx.getClassName());
        code.indent();

        final List<Property> properties = ctx.getProperties();
        for (int i = 0; i < properties.size(); i++) {
            final Property property = properties.get(i);
            final Property actualProperty = getActualTargetProperty(property, ctx);

            final CodeBlock read = ctx.read(actualProperty);
            if (property.type() instanceof PropertyType.EntityType && ctx.shouldExpand(property)) {
                code.add("$T.from($L.$L)", ctx.branch(property).getClassName(), METHOD_FROM_PARAMETER, read);
            } else if (property.type() instanceof PropertyType.EntityCollectionType collectionType) {
                if (ctx.shouldExpand(property)) {
                    final GenerationContext branch = ctx.branch(property);
                    code.add("$L.$L.stream().map($T::from).toList()", METHOD_FROM_PARAMETER, read, branch.getClassName());
                } else {
                    final CodeBlock accessor = ctx.read(property);
                    final CodeBlock.Builder mapper = CodeBlock.builder();
                    if (actualProperty.accessor() instanceof Accessor.MethodAccessor methodAccessor) {
                        mapper.add("$T::$L", collectionType.getEntityType(), methodAccessor.read());
                    } else {
                        mapper.add("e -> e.$L", actualProperty.accessor().read());
                    }

                    code.add("$L.$L.stream().map($L).toList()", METHOD_FROM_PARAMETER, accessor, mapper.build());
                }
            } else {
                code.add("$L.$L", METHOD_FROM_PARAMETER, read);
            }

            if (i < properties.size() - 1) {
                code.add(",\n");
            }
        }

        code.unindent();
        code.add("\n);\n");

        return MethodSpec.methodBuilder("from")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(ParameterSpec.builder(ctx.getUnit().asTypeName(), METHOD_FROM_PARAMETER).build())
                .addCode(code.build())
                .returns(ctx.getClassName())
                .build();
    }

    protected List<TypeSpec> generateBranches(final GenerationContext ctx) {
        return ctx.getBranches()
                .stream()
                .map(this::generateType)
                .toList();
    }

    protected TypeSpec generateType(final GenerationContext ctx) {
        return TypeSpec.recordBuilder(ctx.getClassName())
                .addModifiers(Modifier.PUBLIC)
                .recordConstructor(createConstructor(ctx))
                .addMethod(createFromMethod(ctx))
                .addTypes(generateBranches(ctx))
                .build();
    }

    public void generate(final GenerationContext ctx) throws IOException {
        final TypeSpec spec = generateType(ctx);

        final JavaFile file = JavaFile.builder(ctx.getClassName().packageName(), spec)
                .indent("    ")
                .build();
        file.writeTo(environment.getFiler());
    }

}
