package com.bethibande.process;

import com.bethibande.process.annotation.EntityDTO;
import com.bethibande.process.generation.DTOGenerator;
import com.bethibande.process.generation.GenerationContext;
import com.bethibande.process.model.PersistenceUnit;
import com.google.auto.service.AutoService;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@AutoService(javax.annotation.processing.Processor.class)
public class Processor extends AbstractProcessor {

    protected List<TypeElement> collectTypes(final RoundEnvironment roundEnv) {
        final List<TypeElement> types = new ArrayList<>();
        types.addAll(roundEnv.getElementsAnnotatedWith(EntityDTO.class)
                .stream()
                .filter(el -> el instanceof TypeElement)
                .map(el -> (TypeElement) el)
                .toList());
        types.addAll(roundEnv.getElementsAnnotatedWith(EntityDTO.Repeat.class)
                .stream()
                .filter(el -> el instanceof TypeElement)
                .map(el -> (TypeElement) el)
                .toList());
        return types;
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return true;

        final List<TypeElement> types = collectTypes(roundEnv);

        final EntityAnalyzer analyzer = new EntityAnalyzer(this.processingEnv);
        final DTOGenerator generator = new DTOGenerator(this.processingEnv);
        for (final TypeElement type : types) {
            final PersistenceUnit persistenceUnit = analyzer.analyze(type);

            final List<EntityDTO> requests = new ArrayList<>();
            if (type.getAnnotation(EntityDTO.class) != null) requests.add(type.getAnnotation(EntityDTO.class));
            if (type.getAnnotation(EntityDTO.Repeat.class) != null) requests.addAll(List.of(type.getAnnotation(EntityDTO.Repeat.class).value()));

            for (final EntityDTO request : requests) {
                try {
                    generator.generate(new GenerationContext(
                            null,
                            persistenceUnit,
                            request.excludeProperties(),
                            request.expandProperties()
                    ));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return false;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_25;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of("com.bethibande.process.annotation.*");
    }
}
