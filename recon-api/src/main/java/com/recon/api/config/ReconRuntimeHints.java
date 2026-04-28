package com.recon.api.config;

import com.recon.common.dto.FileArrivedEvent;
import com.recon.storage.entity.ReconFileRegistry;
import com.recon.storage.entity.ReconResult;
import com.recon.storage.entity.ReconStaging;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(ReconRuntimeHints.class)
public class ReconRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(ReconStaging.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.DECLARED_FIELDS);
        hints.reflection().registerType(ReconResult.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.DECLARED_FIELDS);
        hints.reflection().registerType(ReconFileRegistry.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.DECLARED_FIELDS);
        hints.reflection().registerType(FileArrivedEvent.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.DECLARED_FIELDS);
    }
}

