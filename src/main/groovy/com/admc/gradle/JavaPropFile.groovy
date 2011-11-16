package com.admc.gradle;

import org.gradle.api.Project
import org.gradle.api.GradleException

class JavaPropFile {
    private Project gp
    private boolean strict = true
    private boolean expandSystemProperties = true

    JavaPropFile(Project p) {
        gp = p
    }

    void load(File propFile) {
        assert propFile.isFile() && propFile.canRead():
            """Specified properties file inaccessible:  $propFile.absolutePath
"""
        Properties props = new Properties()
        propFile.withInputStream { props.load(it) }
        gp.logger.info(
                'Loaded ' + props.size() + ' from ' + propFile.absolutePath)
        String newVal
        boolean haveNewVal
        int prevCount = props.size()
        def unresolveds = []
        while (prevCount > 0) {
            unresolveds.clear()
            new HashMap(props).each() { pk, pv ->
                haveNewVal = true
                newVal = pv.replaceAll(/\$\{([^}]+)\}/, { matchGrps ->
                    // This block resolves ${references} in property values
                    if (gp.hasProperty(matchGrps[1])
                            && gp.property(matchGrps[1]) instanceof String)
                        return gp.property(matchGrps[1])
                    if (expandSystemProperties
                            && System.properties.containsKey(matchGrps[1]))
                        return System.properties[matchGrps[1]]
                    unresolveds << matchGrps[1]
                    haveNewVal = false
                    return matchGrps[0]
                })
                if (haveNewVal) {
                    if (gp.hasProperty(pk)) {
                        if (gp.property(pk) == null)
                            throw new GradleException(
                            "Property setting '$pk' attempts to override null property value")
                        if (! gp.property(pk) instanceof String)
                            throw new GradleException(
                            "Property setting '$pk' attempts to override non-String property value: ${gp.property(pk).class.name}")
                    }
                    gp.setProperty(pk, newVal)
                    props.remove(pk)
                }
            }
            if (prevCount == props.size()) {
                if (strict)
                    throw new GradleException(
                        'Unable to resolve top-level properties: ' + props.keySet()
                        + '\ndue to unresolved references to: ' + unresolveds)
                gp.logger.error(
                        'Unable to resolve top-level properties: ' + props.keySet()
                        + '\ndue to unresolved references to: ' + unresolveds)
                return
            }
            prevCount = props.size()
        }
    }

    void traditionalPropertiesInit() {
        boolean originalStrictness = strict;
        File appPropertiesFile = gp.file('app.properties')
        File localPropertiesFile = gp.file('local.properties')
        // Unlike Ant, the LAST loaded properties will override.
        strict = true;
        if (appPropertiesFile.exists()) load(appPropertiesFile)
        strict = false;
        if (localPropertiesFile.exists()) load(localPropertiesFile)
        strict = originalStrictness
    }
}
