package com.bethibande.process.generation;

import com.bethibande.process.model.Property;
import com.bethibande.process.model.PropertyType;
import com.palantir.javapoet.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.*;

public class DTOGenerator {

    public static final String METHOD_FROM_PARAMETER = "entity";

    public static final ClassName BEAN_VALIDATION_NOT_NULL = ClassName.get("jakarta.validation.constraints", "NotNull");

    private ProcessingEnvironment environment;

    public DTOGenerator(final ProcessingEnvironment environment) {
        this.environment = environment;
    }

    protected boolean hasJakartaValidationSupport() {
        return environment.getElementUtils().getTypeElement(BEAN_VALIDATION_NOT_NULL.toString()) != null;
    }

    protected String toDTOPropertyName(final Property property, final GenerationContext ctx) {
        if (property.type() instanceof PropertyType.EntityType && !ctx.shouldExpand(property)) {
            return property.name() + "Id";
        }
        return property.name();
    }

    protected TypeName toDTOPropertyType(final Property property, final GenerationContext ctx) {
        final List<AnnotationSpec> annotations = new ArrayList<>(0);
        final boolean hasJakartaValidationSupport = hasJakartaValidationSupport();
        if (!property.optional() && hasJakartaValidationSupport) {
            annotations.add(AnnotationSpec.builder(BEAN_VALIDATION_NOT_NULL).build());
        }

        if (property.type() instanceof PropertyType.EntityType entityType) {
            if (!ctx.shouldExpand(property)) {
                return TypeName.get(entityType.getTargetRef()
                                .idProperty()
                                .type()
                                .getType())
                        .annotated(annotations);
            } else {
                return ctx.branch(property).getClassName().annotated(annotations);
            }
        }

        if (property.type() instanceof PropertyType.EntityCollectionType collectionType) {
            final List<AnnotationSpec> baseTypeAnnotations = hasJakartaValidationSupport
                    ? List.of(AnnotationSpec.builder(BEAN_VALIDATION_NOT_NULL).build())
                    : Collections.emptyList();

            if (!ctx.shouldExpand(property)) {
                final TypeName baseType = TypeName.get(collectionType.getTargetRef()
                                .idProperty()
                                .type()
                                .getType())
                        .annotated(baseTypeAnnotations);

                return ParameterizedTypeName.get(ClassName.get(Collection.class), baseType)
                        .annotated(annotations);
            } else {
                return ParameterizedTypeName.get(
                                ClassName.get(Collection.class),
                                ctx.branch(property).getClassName().annotated(baseTypeAnnotations)
                        )
                        .annotated(annotations);
            }
        }

        return TypeName.get(property.type().getType()).annotated(annotations);
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

    protected CodeBlock directAccess(final Property property, final boolean terminal, final GenerationContext ctx) {
        final SequencedCollection<CodeBlock> accessors = ctx.read(property);
        if (accessors.size() == 1 && terminal) {
            return CodeBlock.of("$L.$L", METHOD_FROM_PARAMETER, accessors.getFirst());
        }

        final CodeBlock.Builder builder = CodeBlock.builder();
        builder.add("$T.ofNullable($L.$L)", Optional.class, METHOD_FROM_PARAMETER, accessors.removeFirst());
        for (final CodeBlock accessor : accessors) {
            builder.add(".map(o -> o.$L)", accessor);
        }
        if (terminal) builder.add(".orElse(null)");
        return builder.build();
    }

    protected CodeBlock dtoMappingAccess(final Property property, final GenerationContext ctx) {
        final CodeBlock accessor = directAccess(property, true, ctx);
        return CodeBlock.of("$T.from($L)", ctx.branch(property).getClassName(), accessor);
    }

    protected CodeBlock collectionAccess(final Property property, final Property actualProperty, final GenerationContext ctx) {
        final CodeBlock.Builder mapper = CodeBlock.builder();
        final CodeBlock.Builder accessor = CodeBlock.builder();
        final boolean shouldExpand = ctx.shouldExpand(property);
        if (shouldExpand) {
            mapper.add("$T::from", ctx.branch(actualProperty).getClassName());
            accessor.add(directAccess(actualProperty, false, ctx));
        } else {
            mapper.add("v -> v != null ? v.$L : null", actualProperty.accessor().read());
            accessor.add(directAccess(property, false, ctx));
        }

        return CodeBlock.of("$L.map(o -> o.stream().map($L).toList()).orElse(null)", accessor.build(), mapper.build());
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

            code.add(switch (property.type()) {
                case PropertyType.EntityType _ when ctx.shouldExpand(property) -> dtoMappingAccess(actualProperty, ctx);
                case PropertyType.EntityCollectionType _ -> collectionAccess(property, actualProperty, ctx);
                default -> directAccess(actualProperty, true, ctx);
            });

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
