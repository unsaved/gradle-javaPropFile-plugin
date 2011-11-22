package com.admc.gradle

import org.gradle.api.Project
import org.gradle.api.Plugin

class MockPlugin implements Plugin<Project> {
    def void apply(Project p) {
        p.extensions.mockBean = new MockBean()
    }
}
