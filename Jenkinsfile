// 腾讯云 CODING（DevOps）持续集成构建脚本
// 用法：在 CODING 项目「持续集成 → 创建构建计划 → 文本编辑器(Jenkinsfile)」中粘贴本文件，
// 或直接将本仓库根目录的 Jenkinsfile 关联为构建计划。
// 构建环境请在 CODING 构建计划中「选择构建环境 → Android」，
// 该环境已预装 JDK 与 Android SDK；若缺少 android-34 / build-tools;34.0.0，下方会自动补齐。

pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {
        stage('检出代码') {
            steps {
                checkout scm
            }
        }

        stage('准备环境') {
            steps {
                script {
                    def androidHome = env.ANDROID_HOME ?: env.ANDROID_SDK_ROOT
                    echo "ANDROID_HOME=${androidHome}"
                    sh 'java -version'
                    // 若本地已含 android-34 可跳过（|| true 保证不阻断）
                    sh "sdkmanager \"platforms;android-34\" \"build-tools;34.0.0\" || true"
                    sh "yes | sdkmanager --licenses > /dev/null || true"
                }
            }
        }

        stage('编译 Debug APK') {
            steps {
                sh 'chmod +x gradlew'
                // 无 keystore 时打 debug 包（自动生成 debug 签名）；如需 release 请配置凭据
                sh './gradlew assembleDebug --no-daemon'
            }
        }

        stage('编译 Release AAB（可选）') {
            steps {
                script {
                    // 在 CODING 构建计划「变量与缓存」中配置 SIGNING_KEY_BASE64（keystore 的 base64）+ 三个密码变量后自动构建可发布 AAB
                    if (env.SIGNING_KEY_BASE64?.trim()) {
                        sh 'echo "$SIGNING_KEY_BASE64" | base64 -d > keystore.jks'
                        sh 'chmod +x gradlew'
                        sh './gradlew assembleRelease bundleRelease --no-daemon'
                        archiveArtifacts artifacts: 'app/build/outputs/apk/release/*.apk', fingerprint: true
                        archiveArtifacts artifacts: 'app/build/outputs/bundle/release/*.aab', fingerprint: true
                        echo 'Release APK / AAB 已归档，可在构建计划「构件产物」中下载'
                    } else {
                        echo '未配置签名密钥（SIGNING_KEY_BASE64 为空），跳过 release 构建'
                    }
                }
            }
        }

        stage('归档产物') {
            steps {
                archiveArtifacts artifacts: 'app/build/outputs/apk/debug/*.apk', fingerprint: true
                echo 'APK 已归档，可在构建计划「构件产物」中下载'
            }
        }
    }

    post {
        failure {
            echo '构建失败，请查看上方日志'
        }
        success {
            echo '构建成功'
        }
    }
}
