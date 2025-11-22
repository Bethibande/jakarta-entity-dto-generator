package com.bethibande.process.model;

import com.palantir.javapoet.CodeBlock;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;

public sealed interface Accessor permits Accessor.FieldAccessor, Accessor.MethodAccessor {

    Element getRootElement();

    CodeBlock read();

    final class FieldAccessor implements Accessor {

        private final VariableElement element;

        public FieldAccessor(final VariableElement element) {
            this.element = element;
        }

        public VariableElement getElement() {
            return element;
        }

        @Override
        public Element getRootElement() {
            return element;
        }

        @Override
        public CodeBlock read() {
            return CodeBlock.of("$L", element.getSimpleName().toString());
        }
    }

    final class MethodAccessor implements Accessor {

        private final ExecutableElement getter;
        private final ExecutableElement setter;

        public MethodAccessor(final ExecutableElement getter, final ExecutableElement setter) {
            this.getter = getter;
            this.setter = setter;
        }

        public ExecutableElement getGetter() {
            return getter;
        }

        public ExecutableElement getSetter() {
            return setter;
        }

        @Override
        public Element getRootElement() {
            return getter;
        }

        @Override
        public CodeBlock read() {
            return CodeBlock.of("$L()", getter.getSimpleName().toString());
        }
    }

}
