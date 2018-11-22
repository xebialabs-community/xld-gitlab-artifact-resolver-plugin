/**
 * Copyright 2018 XEBIALABS
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.xebialabs.community.xldeploy;

import com.xebialabs.deployit.engine.spi.artifact.resolution.ArtifactResolver;
import com.xebialabs.deployit.engine.spi.artifact.resolution.ArtifactResolver.Resolver;
import com.xebialabs.deployit.engine.spi.artifact.resolution.ResolvedArtifactFile;
import com.xebialabs.deployit.plugin.api.udm.artifact.SourceArtifact;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.xebialabs.deployit.plugin.credentials.Credentials;
import org.apache.commons.codec.binary.Base64;
import org.gitlab.api.GitlabAPI;


import org.gitlab.api.models.GitlabProject;
import org.gitlab.api.models.GitlabRepositoryFile;


@Resolver(protocols = {"gitlab"})
public class GitLabArtifactResolver implements ArtifactResolver {

    @Override
    public ResolvedArtifactFile resolveLocation(SourceArtifact artifact) {
        
        System.out.println("GitLabArtifactResolver:" + artifact.getId());
        
        Credentials credentials = artifact.getCredentials();
        if (credentials == null) {
            throw new RuntimeException("Associate a Token credential to your deployable");
        }

        TokenCredentials tc = (TokenCredentials) credentials;

        GitlabAPI gitlabAPI = GitlabAPI.connect("https://gitlab.com", tc.getToken());
        System.out.println(gitlabAPI);

        try {
            Map<String, String> decodeGitLabURI = decodeGitLabURI(artifact.getFileUri());
            GitlabProject project = gitlabAPI.getProject(decodeGitLabURI.get("project"));
            System.out.println("GitLabArtifactResolver:" + project.getName());

            final GitlabRepositoryFile repositoryFile = gitlabAPI.getRepositoryFile(project, decodeGitLabURI.get("path"), decodeGitLabURI.get("ref"));
            System.out.println("GitLabArtifactResolver repositoryFile = " + repositoryFile);

            return new ResolvedArtifactFile() {
                @Override
                public String getFileName() {
                    return repositoryFile.getFileName();
                }

                @Override
                public InputStream openStream() throws IOException {
                    Base64 decoder = new Base64();
                    return new ByteArrayInputStream(decoder.decode(repositoryFile.getContent()));
                }

                @Override
                public void close() throws IOException {
                    //acmeCloudClient.cleanTempDirs();
                }
            };

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("resolveLocation(" + artifact + ") failed", e);

        }

    }

    static Map<String, String> decodeGitLabURI(String uri) {
        String[] split = uri.split(":");
        if (split.length != 4)
            throw new RuntimeException("invalid gitlab uri (" + uri + ") the format is gitlab:projectid:ref:file");

        Map<String, String> decoded = new HashMap<String, String>();
        decoded.put("scheme", split[0]);
        decoded.put("project", split[1]);
        decoded.put("ref", split[2]);
        decoded.put("path", split[3]);
        return decoded;
    }

    @Override
    public boolean validateCorrectness(SourceArtifact artifact) {
        try {
            return new URI(artifact.getFileUri()).getScheme().equals("gitlab");
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        //curl --header "Private-Token: hUqgKSEqKrq5n1dXxSkN" https://gitlab.com/api/v4/projects/9539714
        //curl --header "Private-Token: hUqgKSEqKrq5n1dXxSkN" https://gitlab.com/api/v4/projects/9539714/repository/files/namespace.yaml?ref=master
        //curl --header "Private-Token: hUqgKSEqKrq5n1dXxSkN" https://gitlab.com/api/v4/projects/9539714/repository/files/namespace.yaml/raw?ref=master

        String url = "gitlab:9539714:master:namespace.yaml";

        System.out.println("split length= " + GitLabArtifactResolver.decodeGitLabURI(url));

        GitlabAPI gitlabAPI = GitlabAPI.connect("https://gitlab.com", "hUqgKSEqKrq5n1dXxSkN");

        System.out.println(gitlabAPI);

        GitlabProject project = gitlabAPI.getProject("9539714");
        System.out.println(project.getName());
        //gitlab:9539714:1.1.0:a/b/c/e/f/namespace.yaml

        GitlabRepositoryFile master = gitlabAPI.getRepositoryFile(project, "a/b/c/d/e/f/namespace.yaml", "master");
        System.out.println("master = " + master);

        Base64 decoder = new Base64();
        byte[] decodedBytes = decoder.decode(master.getContent());
        System.out.println(new String(decodedBytes) + "\n");


    }
}
