package com.admc.gradle

import org.gradle.api.Project
import org.gradle.api.Plugin

class JavaPropFilePlugin implements Plugin<Project> {
    def void apply(Project p) {
        p.extensions.propFileLoader = new JavaPropFile(p)
    }
}
