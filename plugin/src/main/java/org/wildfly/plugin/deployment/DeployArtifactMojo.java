/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.plugin.deployment;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.core.Deployment;
import org.wildfly.plugin.core.DeploymentManager;
import org.wildfly.plugin.core.DeploymentResult;
import org.wildfly.plugin.server.ArtifactResolver;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Deploys an arbitrary artifact to the WildFly application server
 *
 * @author Stuart Douglas
 */
@Mojo(name = "deploy-artifact", requiresProject = false, threadSafe = true)
public class DeployArtifactMojo extends AbstractDeployment {
    /**
     * The artifact to deploys groupId
     */
    @Parameter(required = true, property = PropertyNames.DEPLOYMENT_GROUP_ID)
    private String groupId;

    /**
     * The artifact to deploys artifactId
     */
    @Parameter(required = true, property = PropertyNames.DEPLOYMENT_ARTIFACT_ID)
    private String artifactId;

    /**
     * The artifact to deploys version
     */
    @Parameter(required = true, property = PropertyNames.DEPLOYMENT_VERSION)
    private String version;

    /**
     * The artifact to deploys type
     */
    @Parameter(defaultValue = "jar", property = PropertyNames.DEPLOYMENT_TYPE)
    private String type;

    /**
     * The artifact to deploys classifier
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_CLASSIFIER)
    private String classifier;

    /**
     * Specifies whether force mode should be used or not.
     * </p>
     * If force mode is disabled, the deploy goal will cause a build failure if the application being deployed already
     * exists.
     */
    @Parameter(defaultValue = "true", property = PropertyNames.DEPLOY_FORCE)
    private boolean force;

    @Inject
    private ArtifactResolver artifactResolver;

    /**
     * The resolved dependency file
     */
    private File file;


    @Override
    public void validate(final boolean isDomain) throws MojoDeploymentException {
        super.validate(isDomain);

        if (StringUtils.isEmpty(groupId))
            throw new MojoDeploymentException("deploy-artifact must specify the groupId");
        if (StringUtils.isEmpty(artifactId))
            throw new MojoDeploymentException("deploy-artifact must specify the artifactId");
        if (StringUtils.isEmpty(version))
            fillMissingArtifactVersion();

        String artifactStr = ArtifactResolver.ArtifactNameSplitter.of(null)
                .setGroupId(groupId)
                .setArtifactId(artifactId)
                .setVersion(version)
                .setPackaging(type)
                .setClassifier(classifier)
                .asString();
        file = artifactResolver.resolve(project, artifactStr);
    }

    @Override
    protected File file() {
        return file;
    }

    @Override
    public String goal() {
        return "deploy-artifact";
    }

    @Override
    protected DeploymentResult executeDeployment(final DeploymentManager deploymentManager, final Deployment deployment)
            throws IOException {
        if (force) {
            return deploymentManager.forceDeploy(deployment);
        }

        return deploymentManager.deploy(deployment);
    }

    private void fillMissingArtifactVersion() throws MojoDeploymentException {
        List<Dependency> dependencies = project.getDependencies();
        List<Dependency> managementDependencies = project.getDependencyManagement() == null ? Collections.<Dependency>emptyList()
                : project.getDependencyManagement().getDependencies();

        if (!findDependencyVersion(dependencies, false)
                && (project.getDependencyManagement() == null || !findDependencyVersion(managementDependencies, false))
                && !findDependencyVersion(dependencies, true)
                && (project.getDependencyManagement() == null || !findDependencyVersion(managementDependencies, true))) {
            throw new MojoDeploymentException("Unable to find artifact version of " + groupId + ":"
                    + artifactId + " in either dependency list or in project's dependency management.");
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean findDependencyVersion(List<Dependency> dependencies, boolean looseMatch) {
        for (Dependency dependency : dependencies) {
            if (StringUtils.equals(dependency.getArtifactId(), artifactId)
                    && StringUtils.equals(dependency.getGroupId(), groupId)
                    && (looseMatch || StringUtils.equals(dependency.getClassifier(), classifier))
                    && (looseMatch || StringUtils.equals(dependency.getType(), type))) {
                version = dependency.getVersion();

                return true;
            }
        }

        return false;
    }
}
