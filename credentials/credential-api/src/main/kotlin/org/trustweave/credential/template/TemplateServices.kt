package org.trustweave.credential.template

import org.trustweave.credential.template.internal.DefaultTemplateService

/**
 * Factory object for creating TemplateService instances.
 */
object TemplateServices {
    /**
     * Create a default template service instance.
     */
    fun default(): TemplateService {
        return DefaultTemplateService()
    }
}

