package com.bethibande.process.generation;

import com.bethibande.process.StringUtil;
import com.bethibande.process.model.Accessor;
import com.bethibande.process.model.Property;
import com.bethibande.process.model.PropertyType;
import com.palantir.javapoet.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

public class DTOGenerator {

    public static final String METHOD_FROM_PARAMETER = "entity";

    public static final ClassName BEAN_VALIDATION_NOT_NULL = ClassName.get("jakarta.validation.constraints", "NotNull");
    public static final ClassName ENTITY_MANAGER = ClassName.get("jakarta.persistence", "EntityManager");
    public static final ClassName ENTITY_NOT_FOUND_EXCEPTION = ClassName.get("com.bethibande.process.exception", "EntityNotFoundException");
    public static final ClassName ENTITY_UTILS = ClassName.get("com.bethibande.process.util", "EntityUtils");

    public static AnnotationSpec NOT_NULL_ANNOTATION = AnnotationSpec.builder(BEAN_VALIDATION_NOT_NULL).build();

    private ProcessingEnvironment environment;

    public DTOGenerator(final ProcessingEnvironment environment) {
        this.environment = environment;
    }

    protected boolean hasJakartaValidationSupport() {
        return environment.getElementUtils().getTypeElement(BEAN_VALIDATION_NOT_NULL.toString()) != null;
    }

    protected AnnotationSpec toAnnotationSpec(final AnnotationMirror annotationMirror) {
        final String typeString = annotationMirror.getAnnotationType().toString();
        final ClassName annotationType = ClassName.bestGuess(typeString);

        final AnnotationSpec.Builder builder = AnnotationSpec.builder(annotationType);

        for (final Map.Entry<? extends javax.lang.model.element.ExecutableElement, ? extends AnnotationValue> entry : annotationMirror.getElementValues().entrySet()) {
            final String name = entry.getKey().getSimpleName().toString();
            final AnnotationValue value = entry.getValue();
            builder.addMember(name, toCodeBlock(value));
        }

        return builder.build();
    }

    protected CodeBlock toCodeBlock(final AnnotationValue annotationValue) {
        final Object value = annotationValue.getValue();

        if (value instanceof javax.lang.model.type.TypeMirror typeMirror) {
            return CodeBlock.of("$T", TypeName.get(typeMirror));
        }

        if (value instanceof javax.lang.model.element.Element element) {
            if (element instanceof VariableElement varElement) {
                final String enclosing = varElement.getEnclosingElement().toString();
                return CodeBlock.of("$T.$L",
                        ClassName.bestGuess(enclosing),
                        varElement.getSimpleName());
            }
            return CodeBlock.of("$L", element);
        }

        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return CodeBlock.of("{}");
            }
            final CodeBlock.Builder builder = CodeBlock.builder();
            builder.add("{");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) builder.add(", ");
                builder.add(toCodeBlock((AnnotationValue) list.get(i)));
            }
            builder.add("}");
            return builder.build();
        }

        if (value instanceof AnnotationMirror nestedAnnotation) {
            return CodeBlock.of("$L", toAnnotationSpec(nestedAnnotation));
        }

        if (value instanceof Name name) {
            return CodeBlock.of("$S", name.toString());
        }

        return CodeBlock.of("$L", value);
    }

    protected List<AnnotationSpec> toAnnotationSpecs(final List<AnnotationMirror> annotationMirrors) {
        return annotationMirrors.stream()
                .map(this::toAnnotationSpec)
                .toList();
    }

    protected TypeMirror asType(final Class<?> clazz) {
        return environment.getElementUtils().getTypeElement(clazz.getName()).asType();
    }

    protected boolean isAssignable(final TypeMirror superType, final TypeMirror to) {
        return environment.getTypeUtils().isAssignable(superType, to);
    }

    protected boolean hasIdProperty(final GenerationContext ctx) {
        final Property idProperty = ctx.getUnit().idProperty();
        if (idProperty == null) return false;
        return ctx.getProperties().stream().anyMatch(p -> Objects.equals(p.name(), idProperty.name()));
    }

    protected boolean requiresEntityManager(final GenerationContext ctx) {
        for (final Property property : ctx.getProperties()) {
            if (property.type() instanceof PropertyType.EntityType entityType) {
                if (!ctx.shouldExpand(property)) {
                    if (entityType.getTargetRef() != null && entityType.getTargetRef().idProperty() != null) {
                        return true;
                    }
                } else {
                    final GenerationContext branch = ctx.branch(property);
                    if (branch != null) {
                        if (hasIdProperty(branch)) return true;
                        if (requiresEntityManager(branch)) return true;
                    }
                }
            } else if (property.type() instanceof PropertyType.EntityCollectionType collectionType) {
                if (!ctx.shouldExpand(property)) {
                    if (collectionType.getTargetRef() != null && collectionType.getTargetRef().idProperty() != null) {
                        return true;
                    }
                } else {
                    final GenerationContext branch = ctx.branch(property);
                    if (branch != null) {
                        if (hasIdProperty(branch)) return true;
                        if (requiresEntityManager(branch)) return true;
                    }
                }
            }
        }
        return false;
    }

    protected String toDTOPropertyName(final Property property, final GenerationContext ctx) {
        if (property.type() instanceof PropertyType.EntityType && !ctx.shouldExpand(property)) {
            return property.name() + "Id";
        }
        return property.name();
    }

    protected TypeName toDTOPropertyType(final Property property, final GenerationContext ctx) {
        final List<AnnotationSpec> annotations = new ArrayList<>(new LinkedHashSet<>(toAnnotationSpecs(property.validationAnnotations())));
        final boolean hasJakartaValidationSupport = hasJakartaValidationSupport();

        // Auto-add @NotNull for non-optional properties only if not already present
        if (!property.optional() && hasJakartaValidationSupport && !annotations.contains(NOT_NULL_ANNOTATION)) {
            annotations.add(NOT_NULL_ANNOTATION);
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
                    ? List.of(NOT_NULL_ANNOTATION)
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

    protected List<Property> getEmbeddedParents(final Property property) {
        final List<Property> parents = new ArrayList<>();
        Property current = property.parent();
        while (current != null) {
            if (current.type() instanceof PropertyType.EntityType || current.type() instanceof PropertyType.EntityCollectionType) {
                break;
            }
            parents.add(0, current);
            current = current.parent();
        }
        return parents;
    }

    protected String generateEmbeddedParentInit(final Property property, final CodeBlock.Builder code) {
        final List<Property> parents = getEmbeddedParents(property);
        if (parents.isEmpty()) {
            return "entity";
        }
        String currentAccess = "entity";
        for (final Property parent : parents) {
            if (parent.accessor() instanceof Accessor.FieldAccessor fieldAccessor) {
                final String fieldName = fieldAccessor.getElement().getSimpleName().toString();
                final String nextAccess = currentAccess + "." + fieldName;
                code.add("if ($L == null) {\n", nextAccess);
                code.indent();
                code.addStatement("$L = new $T()", nextAccess, parent.type().getType());
                code.unindent();
                code.add("}\n");
                currentAccess = nextAccess;
            } else if (parent.accessor() instanceof Accessor.MethodAccessor methodAccessor) {
                final String getterName = methodAccessor.getGetter().getSimpleName().toString();
                final String nextAccess = currentAccess + "." + getterName + "()";
                if (methodAccessor.getSetter() != null) {
                    final String setterName = methodAccessor.getSetter().getSimpleName().toString();
                    code.add("if ($L == null) {\n", nextAccess);
                    code.indent();
                    code.addStatement("$L.$L(new $T())", currentAccess, setterName, parent.type().getType());
                    code.unindent();
                    code.add("}\n");
                }
                currentAccess = nextAccess;
            }
        }
        return currentAccess;
    }

    protected void generateSetProperty(final Property property, final String targetExpr, final Object valueExpr, final CodeBlock.Builder code) {
        if (property.accessor() instanceof Accessor.FieldAccessor fieldAccessor) {
            final String fieldName = fieldAccessor.getElement().getSimpleName().toString();
            if (valueExpr instanceof CodeBlock cb) {
                code.addStatement("$L.$L = $L", targetExpr, fieldName, cb);
            } else {
                code.addStatement("$L.$L = $L", targetExpr, fieldName, valueExpr);
            }
        } else if (property.accessor() instanceof Accessor.MethodAccessor methodAccessor) {
            if (methodAccessor.getSetter() != null) {
                final String setterName = methodAccessor.getSetter().getSimpleName().toString();
                if (valueExpr instanceof CodeBlock cb) {
                    code.addStatement("$L.$L($L)", targetExpr, setterName, cb);
                } else {
                    code.addStatement("$L.$L($L)", targetExpr, setterName, valueExpr);
                }
            }
        }
    }

    protected void generateDirectPropertyAssignment(final Property property, final GenerationContext ctx, final CodeBlock.Builder code) {
        final String targetExpr = generateEmbeddedParentInit(property, code);
        final String dtoPropName = toDTOPropertyName(property, ctx);
        generateSetProperty(property, targetExpr, "this." + dtoPropName + "()", code);
    }

    protected void generateEntityTypeAssignment(final Property property, final PropertyType.EntityType entityType, final GenerationContext ctx, final CodeBlock.Builder code) {
        final String targetExpr = generateEmbeddedParentInit(property, code);
        final String dtoPropName = toDTOPropertyName(property, ctx);
        final TypeName targetEntityTypeName = entityType.getTargetRef().asTypeName();
        final String varName = property.name() + "Ref";
        final Property targetIdProp = entityType.getTargetRef().idProperty();

        if (!ctx.shouldExpand(property)) {
            code.add("if (this.$L() != null) {\n", dtoPropName);
            code.indent();
            final String existingVarName = "existing" + StringUtil.firstCharacterUpperCase(varName);
            code.addStatement("final $T $L = $L.$L", targetEntityTypeName, existingVarName, targetExpr, property.accessor().read());
            code.addStatement("final $T $L", targetEntityTypeName, varName);
            if (targetIdProp != null) {
                code.add("if ($L != null && $T.equals(this.$L(), $L.$L)) {\n", existingVarName, Objects.class, dtoPropName, existingVarName, targetIdProp.accessor().read());
                code.indent();
                code.addStatement("$L = $L", varName, existingVarName);
                code.unindent();
                code.add("} else {\n");
                code.indent();
                code.addStatement("$L = entityManager.find($T.class, this.$L())", varName, targetEntityTypeName, dtoPropName);
                code.add("if ($L == null) {\n", varName);
                code.indent();
                code.addStatement("throw new $T($S, $T.class, this.$L())", ENTITY_NOT_FOUND_EXCEPTION, property.getPathFromRootString(), targetEntityTypeName, dtoPropName);
                code.unindent();
                code.add("}\n");
                code.unindent();
                code.add("}\n");
            } else {
                code.addStatement("$L = entityManager.find($T.class, this.$L())", varName, targetEntityTypeName, dtoPropName);
                code.add("if ($L == null) {\n", varName);
                code.indent();
                code.addStatement("throw new $T($S, $T.class, this.$L())", ENTITY_NOT_FOUND_EXCEPTION, property.getPathFromRootString(), targetEntityTypeName, dtoPropName);
                code.unindent();
                code.add("}\n");
            }
            generateSetProperty(property, targetExpr, varName, code);
            code.unindent();
            code.add("} else {\n");
            code.indent();
            generateSetProperty(property, targetExpr, "null", code);
            code.unindent();
            code.add("}\n");
        } else {
            final GenerationContext branch = ctx.branch(property);
            final boolean branchHasId = hasIdProperty(branch);
            final boolean branchNeedsEm = requiresEntityManager(branch);
            final Property branchIdProp = branch.getUnit().idProperty();
            final String branchIdGetter = branchIdProp != null ? toDTOPropertyName(branchIdProp, branch) : "id";

            if (branchHasId) {
                code.add("if (this.$L() != null) {\n", dtoPropName);
                code.indent();
                code.add("if (this.$L().$L() != null) {\n", dtoPropName, branchIdGetter);
                code.indent();
                final String existingVarName = "existing" + StringUtil.firstCharacterUpperCase(varName);
                code.addStatement("final $T $L = $L.$L", targetEntityTypeName, existingVarName, targetExpr, property.accessor().read());
                code.addStatement("final $T $L", targetEntityTypeName, varName);
                if (branchIdProp != null) {
                    code.add("if ($L != null && $T.equals(this.$L().$L(), $L.$L)) {\n", existingVarName, Objects.class, dtoPropName, branchIdGetter, existingVarName, branchIdProp.accessor().read());
                    code.indent();
                    code.addStatement("$L = $L", varName, existingVarName);
                    code.unindent();
                    code.add("} else {\n");
                    code.indent();
                    code.addStatement("$L = entityManager.find($T.class, this.$L().$L())", varName, targetEntityTypeName, dtoPropName, branchIdGetter);
                    code.add("if ($L == null) {\n", varName);
                    code.indent();
                    code.addStatement("throw new $T($S, $T.class, this.$L().$L())", ENTITY_NOT_FOUND_EXCEPTION, property.getPathFromRootString(), targetEntityTypeName, dtoPropName, branchIdGetter);
                    code.unindent();
                    code.add("}\n");
                    code.unindent();
                    code.add("}\n");
                } else {
                    code.addStatement("$L = entityManager.find($T.class, this.$L().$L())", varName, targetEntityTypeName, dtoPropName, branchIdGetter);
                    code.add("if ($L == null) {\n", varName);
                    code.indent();
                    code.addStatement("throw new $T($S, $T.class, this.$L().$L())", ENTITY_NOT_FOUND_EXCEPTION, property.getPathFromRootString(), targetEntityTypeName, dtoPropName, branchIdGetter);
                    code.unindent();
                    code.add("}\n");
                }
                final String callToEntity = branchNeedsEm
                        ? "this." + dtoPropName + "().toEntity(" + varName + ", entityManager)"
                        : "this." + dtoPropName + "().toEntity(" + varName + ")";
                generateSetProperty(property, targetExpr, callToEntity, code);
                code.unindent();
                code.add("} else {\n");
                code.indent();
                generateSetProperty(property, targetExpr, "null", code);
                code.unindent();
                code.add("}\n");
                code.unindent();
                code.add("} else {\n");
                code.indent();
                generateSetProperty(property, targetExpr, "null", code);
                code.unindent();
                code.add("}\n");
            } else {
                code.add("if (this.$L() != null) {\n", dtoPropName);
                code.indent();
                final String callToEntity = branchNeedsEm
                        ? "this." + dtoPropName + "().toEntity(entityManager)"
                        : "this." + dtoPropName + "().toEntity()";
                generateSetProperty(property, targetExpr, callToEntity, code);
                code.unindent();
                code.add("} else {\n");
                code.indent();
                generateSetProperty(property, targetExpr, "null", code);
                code.unindent();
                code.add("}\n");
            }
        }
    }

    protected void generateEntityCollectionAssignment(final Property property, final PropertyType.EntityCollectionType collectionType, final GenerationContext ctx, final CodeBlock.Builder code) {
        final String targetExpr = generateEmbeddedParentInit(property, code);
        final String dtoPropName = toDTOPropertyName(property, ctx);
        final TypeName targetEntityTypeName = collectionType.getTargetRef().asTypeName();
        final Property idProp = collectionType.getTargetRef().idProperty();
        final TypeName idTypeName = idProp != null ? TypeName.get(idProp.type().getType()) : TypeName.get(Object.class);
        final boolean isSet = isAssignable(environment.getTypeUtils().erasure(collectionType.getType()), asType(Set.class));
        final ClassName colInterface = isSet ? ClassName.get(Set.class) : ClassName.get(List.class);
        final ClassName colImpl = isSet ? ClassName.get(LinkedHashSet.class) : ClassName.get(ArrayList.class);
        final String listVarName = property.name() + (isSet ? "Set" : "List");
        final String mapVarName = property.name() + "RefMap";

        code.add("if (this.$L() != null) {\n", dtoPropName);
        code.indent();

        if (!ctx.shouldExpand(property)) {
            final String idAttrName = idProp != null ? idProp.name() : "id";
            final CodeBlock idExtractor = idProp != null ? CodeBlock.of("e -> e.$L", idProp.accessor().read()) : CodeBlock.of("e -> null");
            code.addStatement("final $T<$T, $T> $L = $T.findAllById(entityManager, $T.class, $S, $L, this.$L())",
                    Map.class, idTypeName, targetEntityTypeName, mapVarName, ENTITY_UTILS, targetEntityTypeName, idAttrName, idExtractor, dtoPropName);
            code.addStatement("final $T<$T> $L = new $T<>(this.$L().size())", colInterface, targetEntityTypeName, listVarName, colImpl, dtoPropName);

            code.add("for (final $T id : this.$L()) {\n", idTypeName, dtoPropName);
            code.indent();
            code.add("if (id != null) {\n");
            code.indent();
            code.addStatement("final $T item = $L.get(id)", targetEntityTypeName, mapVarName);
            code.add("if (item == null) {\n");
            code.indent();
            code.addStatement("throw new $T($S, $T.class, id)", ENTITY_NOT_FOUND_EXCEPTION, property.getPathFromRootString(), targetEntityTypeName);
            code.unindent();
            code.add("}\n");
            code.addStatement("$L.add(item)", listVarName);
            code.unindent();
            code.add("} else {\n");
            code.indent();
            code.addStatement("$L.add(null)", listVarName);
            code.unindent();
            code.add("}\n");
            code.unindent();
            code.add("}\n");
        } else {
            final GenerationContext branch = ctx.branch(property);
            final boolean branchHasId = hasIdProperty(branch);
            final boolean branchNeedsEm = requiresEntityManager(branch);
            final Property branchIdProp = branch.getUnit().idProperty();
            final String branchIdGetter = branchIdProp != null ? toDTOPropertyName(branchIdProp, branch) : "id";
            final TypeName itemDtoType = branch.getClassName();

            if (branchHasId) {
                final String idAttrName = branchIdProp != null ? branchIdProp.name() : "id";
                final CodeBlock idExtractor = branchIdProp != null ? CodeBlock.of("e -> e.$L", branchIdProp.accessor().read()) : CodeBlock.of("e -> null");
                final String idsExpr = "this." + dtoPropName + "().stream().filter($T::nonNull).map(item -> item." + branchIdGetter + "()).filter($T::nonNull).toList()";
                code.addStatement("final $T<$T, $T> $L = $T.findAllById(entityManager, $T.class, $S, $L, " + idsExpr + ")",
                        Map.class, idTypeName, targetEntityTypeName, mapVarName, ENTITY_UTILS, targetEntityTypeName, idAttrName, idExtractor, Objects.class, Objects.class);
            }

            code.addStatement("final $T<$T> $L = new $T<>(this.$L().size())", colInterface, targetEntityTypeName, listVarName, colImpl, dtoPropName);
            code.add("for (final $T itemDto : this.$L()) {\n", itemDtoType, dtoPropName);
            code.indent();
            code.add("if (itemDto != null) {\n");
            code.indent();
            if (branchHasId) {
                code.add("if (itemDto.$L() != null) {\n", branchIdGetter);
                code.indent();
                code.addStatement("final $T item = $L.get(itemDto.$L())", targetEntityTypeName, mapVarName, branchIdGetter);
                code.add("if ($L == null) {\n", "item");
                code.indent();
                code.addStatement("throw new $T($S, $T.class, itemDto.$L())", ENTITY_NOT_FOUND_EXCEPTION, property.getPathFromRootString(), targetEntityTypeName, branchIdGetter);
                code.unindent();
                code.add("}\n");
                final String callToEntity = branchNeedsEm
                        ? "itemDto.toEntity(item, entityManager)"
                        : "itemDto.toEntity(item)";
                code.addStatement("$L.add($L)", listVarName, callToEntity);
                code.unindent();
                code.add("} else {\n");
                code.indent();
                code.addStatement("$L.add(null)", listVarName);
                code.unindent();
                code.add("}\n");
            } else {
                final String callToEntity = branchNeedsEm
                        ? "itemDto.toEntity(entityManager)"
                        : "itemDto.toEntity()";
                code.addStatement("$L.add($L)", listVarName, callToEntity);
            }
            code.unindent();
            code.add("} else {\n");
            code.indent();
            code.addStatement("$L.add(null)", listVarName);
            code.unindent();
            code.add("}\n");
            code.unindent();
            code.add("}\n");
        }

        generateSetProperty(property, targetExpr, listVarName, code);
        code.unindent();
        code.add("} else {\n");
        code.indent();
        generateSetProperty(property, targetExpr, "null", code);
        code.unindent();
        code.add("}\n");
    }

    protected MethodSpec createToEntityMethod(final GenerationContext ctx) {
        final boolean hasId = hasIdProperty(ctx);
        final boolean needsEm = requiresEntityManager(ctx);

        final MethodSpec.Builder method = MethodSpec.methodBuilder("toEntity")
                .addModifiers(Modifier.PUBLIC)
                .returns(ctx.getUnit().asTypeName());

        if (hasId) {
            method.addParameter(ParameterSpec.builder(ctx.getUnit().asTypeName(), "existing", Modifier.FINAL).build());
        }
        if (needsEm) {
            method.addParameter(ParameterSpec.builder(ENTITY_MANAGER, "entityManager", Modifier.FINAL).build());
            method.addException(ENTITY_NOT_FOUND_EXCEPTION);
        }

        final CodeBlock.Builder code = CodeBlock.builder();
        if (hasId) {
            code.addStatement("if (existing == null) return null");
            code.addStatement("final $T entity = existing", ctx.getUnit().asTypeName());
        } else {
            code.addStatement("final $T entity = new $T()", ctx.getUnit().asTypeName(), ctx.getUnit().asTypeName());
        }

        for (final Property property : ctx.getProperties()) {
            if (property.type() instanceof PropertyType.EntityType entityType) {
                generateEntityTypeAssignment(property, entityType, ctx, code);
            } else if (property.type() instanceof PropertyType.EntityCollectionType collectionType) {
                generateEntityCollectionAssignment(property, collectionType, ctx, code);
            } else {
                generateDirectPropertyAssignment(property, ctx, code);
            }
        }

        code.addStatement("return entity");
        method.addCode(code.build());

        return method.build();
    }

    protected MethodSpec createToEntityFromEntityManagerMethod(final GenerationContext ctx) {
        final boolean needsEm = requiresEntityManager(ctx);
        final Property idProp = ctx.getUnit().idProperty();
        final String idGetter = idProp != null ? toDTOPropertyName(idProp, ctx) : "id";
        final String idFieldPath = idProp != null ? idProp.getPathFromRootString() : "id";
        final TypeName entityTypeName = ctx.getUnit().asTypeName();

        final CodeBlock.Builder code = CodeBlock.builder();
        code.addStatement("if (this.$L() == null) return null", idGetter);
        code.addStatement("final $T existing = entityManager.find($T.class, this.$L())", entityTypeName, entityTypeName, idGetter);
        code.add("if (existing == null) {\n");
        code.indent();
        code.addStatement("throw new $T($S, $T.class, this.$L())", ENTITY_NOT_FOUND_EXCEPTION, idFieldPath, entityTypeName, idGetter);
        code.unindent();
        code.add("}\n");

        if (needsEm) {
            code.addStatement("return this.toEntity(existing, entityManager)");
        } else {
            code.addStatement("return this.toEntity(existing)");
        }

        return MethodSpec.methodBuilder("toEntity")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterSpec.builder(ENTITY_MANAGER, "entityManager", Modifier.FINAL).build())
                .addException(ENTITY_NOT_FOUND_EXCEPTION)
                .returns(entityTypeName)
                .addCode(code.build())
                .build();
    }

    protected List<TypeSpec> generateBranches(final GenerationContext ctx) {
        return ctx.getBranches()
                .stream()
                .map(this::generateType)
                .toList();
    }

    protected TypeSpec generateType(final GenerationContext ctx) {
        final TypeSpec.Builder builder = TypeSpec.recordBuilder(ctx.getClassName())
                .addModifiers(Modifier.PUBLIC)
                .recordConstructor(createConstructor(ctx))
                .addMethod(createFromMethod(ctx))
                .addMethod(createToEntityMethod(ctx));

        if (hasIdProperty(ctx)) {
            builder.addMethod(createToEntityFromEntityManagerMethod(ctx));
        }

        return builder
                .addTypes(generateBranches(ctx))
                .build();
    }

    public void generate(final GenerationContext ctx) throws IOException {
        final TypeSpec spec = generateType(ctx);

        final JavaFile file = JavaFile.builder(ctx.getClassName().packageName(), spec)
                .indent("    ")
                .build();

        final JavaFileObject sourceFile = environment.getFiler()
                .createSourceFile(ctx.getClassName().toString(), ctx.getUnit().element());

        try (final Writer writer = sourceFile.openWriter()) {
            file.writeTo(writer);
        }
    }

}
