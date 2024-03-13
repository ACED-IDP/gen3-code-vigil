/*
    String parameter NAMESPACE
        e.g., qa-anvil
    String parameter SERVICE
        Key from the manifest's versions block
        e.g., metadata
    String parameter VERSION
        Version, specifically the quay image tag
        e.g., 2023.09
*/
pipeline {
    agent {
        node {
        label 'gen3-ci-worker'
      }
    }
    stages {
        stage('Clean old workspace') {
            steps {
                cleanWs()
            }
        }
        stage('Initial setup') {
            steps {
                script {
                    sh '''#!/bin/bash +x
                        set -e
                        echo NAMESPACE: $NAMESPACE
                    '''
                }
                // cloud-automation
                checkout([
                  $class: 'GitSCM',
                  branches: [[name: 'refs/heads/master']],
                  doGenerateSubmoduleConfigurations: false,
                  extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'cloud-automation']],
                  submoduleCfg: [],
                  userRemoteConfigs: [[credentialsId: 'PlanXCyborgUserJenkins', url: 'https://github.com/uc-cdis/cloud-automation.git']]
                ])
                // gitops-qa
                checkout([
                  $class: 'GitSCM',
                  branches: [[name: 'refs/heads/master']],
                  doGenerateSubmoduleConfigurations: false,
                  extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'cdis-manifest']],
                  submoduleCfg: [],
                  userRemoteConfigs: [[credentialsId: 'PlanXCyborgUserJenkins', url: 'https://github.com/uc-cdis/gitops-qa.git']]
                ])
            }
        }
        stage('Change service version') {
            steps {
                dir("cdis-manifest/${NAMESPACE}.planx-pla.net") {
                    script {
                        currentBranch = "${SERVICE}:[a-zA-Z0-9._-]*"
                        targetBranch = "${SERVICE}:${VERSION}"
                        echo "Editing cdis-manifest/${NAMESPACE} service ${SERVICE} to version ${VERSION}"
                        sh 'sed -i -e "s,'+"${currentBranch},${targetBranch}"+',g" manifest.json'
                        sh 'cat manifest.json'
                    }
                }
            }
        }
        stage('Roll the environment') {
            steps {
                dir("cdis-manifest/${NAMESPACE}.planx-pla.net") {
                    script {
                        sh '''#!/bin/bash +x
                            set -e
                            export GEN3_HOME=\$WORKSPACE/cloud-automation
                            export KUBECTL_NAMESPACE=\${NAMESPACE}
                            source $GEN3_HOME/gen3/gen3setup.sh
                            yes | gen3 reset
                        '''
                    }
                }
            }
        }
        stage('Run usersync') {
            steps {
                dir("cdis-manifest/${NAMESPACE}.planx-pla.net") {
                    script {
                        sh '''#!/bin/bash +x
                            set -e
                            export GEN3_HOME=\$WORKSPACE/cloud-automation
                            export KUBECTL_NAMESPACE=\${NAMESPACE}
                            source $GEN3_HOME/gen3/gen3setup.sh
                            gen3 job run usersync
                            g3kubectl wait --for=condition=complete --timeout=-1s jobs/usersync
                        '''
                    }
                }
            }
        }
    }
}
