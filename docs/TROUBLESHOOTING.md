Run ./gradlew assembleDebug --stacktrace
  ./gradlew assembleDebug --stacktrace
  shell: /usr/bin/bash -e {0}
  env:
    JAVA_HOME: /opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/17.0.20-1/x64
    JAVA_HOME_17_X64: /opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/17.0.20-1/x64
    GRADLE_ACTION_ID: gradle/actions/setup-gradle
    GRADLE_BUILD_ACTION_SETUP_COMPLETED: true
    GRADLE_BUILD_ACTION_CACHE_RESTORED: true
    DEVELOCITY_INJECTION_INIT_SCRIPT_NAME: gradle-actions.inject-develocity.init.gradle
    DEVELOCITY_AUTO_INJECTION_CUSTOM_VALUE: gradle-actions
    GITHUB_DEPENDENCY_GRAPH_ENABLED: false
Downloading https://services.gradle.org/distributions/gradle-8.4-bin.zip
............10%............20%.............30%............40%.............50%............60%.............70%............80%.............90%............100%

Welcome to Gradle 8.4!

Here are the highlights of this release:
 - Compiling and testing with Java 21
 - Faster Java compilation on Windows
 - Role focused dependency configurations creation

For more details see https://docs.gradle.org/8.4/release-notes.html

Starting a Gradle Daemon (subsequent builds will be faster)
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:mergeDebugNativeDebugMetadata NO-SOURCE
> Task :app:checkKotlinGradlePluginConfigurationErrors
> Task :app:dataBindingMergeDependencyArtifactsDebug
> Task :app:generateDebugResValues
> Task :app:generateDebugResources
> Task :app:packageDebugResources
> Task :app:mergeDebugResources
> Task :app:parseDebugLocalResources
> Task :app:checkDebugAarMetadata
> Task :app:dataBindingGenBaseClassesDebug
> Task :app:mapDebugSourceSetPaths
> Task :app:createDebugCompatibleScreenManifests
> Task :app:extractDeepLinksDebug
> Task :app:processDebugMainManifest
> Task :app:processDebugManifest
> Task :app:javaPreCompileDebug
> Task :app:mergeDebugShaders
> Task :app:compileDebugShaders NO-SOURCE
> Task :app:generateDebugAssets UP-TO-DATE
> Task :app:mergeDebugAssets
> Task :app:processDebugManifestForPackage
> Task :app:compressDebugAssets
> Task :app:desugarDebugFileDependencies
> Task :app:processDebugResources
> Task :app:checkDebugDuplicateClasses

e: file:///home/runner/work/yt-wa/yt-wa/app/src/main/java/com/brave/LiteTube/MainActivity.kt:235:37 This type is final, so it cannot be inherited from
e: file:///home/runner/work/yt-wa/yt-wa/app/src/main/java/com/brave/LiteTube/MainActivity.kt:246:22 Unresolved reference. None of the following candidates is applicable because of receiver type mismatch: 
public final var onPageFinishedCallback: ((url: String?) -> Unit)? defined in com.brave.LiteTube.MainActivity.setupBrowseWebView.`<no name provided>`
e: file:///home/runner/work/yt-wa/yt-wa/app/src/main/java/com/brave/LiteTube/MainActivity.kt:251:22 Unresolved reference. None of the following candidates is applicable because of receiver type mismatch: 
public final fun installDocumentStartScripts(webView: WebView): Unit defined in com.brave.LiteTube.MainActivity.setupBrowseWebView.`<no name provided>`
> Task :app:compileDebugKotlin

> Task :app:compileDebugKotlin FAILED
> Task :app:mergeExtDexDebug

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:compileDebugKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

* Try:
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

* Exception is:
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':app:compileDebugKotlin'.
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.lambda$executeIfValid$1(ExecuteActionsTaskExecuter.java:148)
	at org.gradle.internal.Try$Failure.ifSuccessfulOrElse(Try.java:282)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeIfValid(ExecuteActionsTaskExecuter.java:146)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:134)
	at org.gradle.api.internal.tasks.execution.FinalizePropertiesTaskExecuter.execute(FinalizePropertiesTaskExecuter.java:46)
	at org.gradle.api.internal.tasks.execution.ResolveTaskExecutionModeExecuter.execute(ResolveTaskExecutionModeExecuter.java:51)
24 actionable tasks: 24 executed
	at org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter.execute(SkipTaskWithNoActionsExecuter.java:57)
	at org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter.execute(SkipOnlyIfTaskExecuter.java:74)
	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.executeTask(EventFiringTaskExecuter.java:77)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:55)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:52)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:199)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:157)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
	at org.gradle.internal.operations.DefaultBuildOperationExecutor.call(DefaultBuildOperationExecutor.java:78)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter.execute(EventFiringTaskExecuter.java:52)
	at org.gradle.execution.plan.LocalTaskNodeExecutor.execute(LocalTaskNodeExecutor.java:42)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:331)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:318)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.lambda$execute$0(DefaultTaskExecutionGraph.java:314)
	at org.gradle.internal.operations.CurrentBuildOperationRef.with(CurrentBuildOperationRef.java:80)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:314)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:303)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.execute(DefaultPlanExecutor.java:463)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.run(DefaultPlanExecutor.java:380)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:47)
Caused by: org.gradle.workers.internal.DefaultWorkerExecutor$WorkExecutionException: A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
	at org.gradle.workers.internal.DefaultWorkerExecutor$WorkItemExecution.waitForCompletion(DefaultWorkerExecutor.java:283)
	at org.gradle.internal.work.DefaultAsyncWorkTracker.lambda$waitForItemsAndGatherFailures$2(DefaultAsyncWorkTracker.java:130)
	at org.gradle.internal.Factories$1.create(Factories.java:31)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withoutLocks(DefaultWorkerLeaseService.java:336)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withoutLocks(DefaultWorkerLeaseService.java:319)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withoutLock(DefaultWorkerLeaseService.java:324)
	at org.gradle.internal.work.DefaultAsyncWorkTracker.waitForItemsAndGatherFailures(DefaultAsyncWorkTracker.java:126)
	at org.gradle.internal.work.DefaultAsyncWorkTracker.waitForItemsAndGatherFailures(DefaultAsyncWorkTracker.java:92)
	at org.gradle.internal.work.DefaultAsyncWorkTracker.waitForAll(DefaultAsyncWorkTracker.java:78)
	at org.gradle.internal.work.DefaultAsyncWorkTracker.waitForCompletion(DefaultAsyncWorkTracker.java:66)
	at org.gradle.api.internal.tasks.execution.TaskExecution$3.run(TaskExecution.java:256)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:29)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:26)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:157)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:47)
	at org.gradle.internal.operations.DefaultBuildOperationExecutor.run(DefaultBuildOperationExecutor.java:73)
	at org.gradle.api.internal.tasks.execution.TaskExecution.executeAction(TaskExecution.java:233)
	at org.gradle.api.internal.tasks.execution.TaskExecution.executeActions(TaskExecution.java:216)
	at org.gradle.api.internal.tasks.execution.TaskExecution.executeWithPreviousOutputFiles(TaskExecution.java:199)
	at org.gradle.api.internal.tasks.execution.TaskExecution.execute(TaskExecution.java:166)
	at org.gradle.internal.execution.steps.ExecuteStep.executeInternal(ExecuteStep.java:105)
	at org.gradle.internal.execution.steps.ExecuteStep.access$000(ExecuteStep.java:44)
	at org.gradle.internal.execution.steps.ExecuteStep$1.call(ExecuteStep.java:59)
	at org.gradle.internal.execution.steps.ExecuteStep$1.call(ExecuteStep.java:56)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:199)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:157)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
	at org.gradle.internal.operations.DefaultBuildOperationExecutor.call(DefaultBuildOperationExecutor.java:78)
	at org.gradle.internal.execution.steps.ExecuteStep.execute(ExecuteStep.java:56)
	at org.gradle.internal.execution.steps.ExecuteStep.execute(ExecuteStep.java:44)
	at org.gradle.internal.execution.steps.RemovePreviousOutputsStep.execute(RemovePreviousOutputsStep.java:67)
	at org.gradle.internal.execution.steps.RemovePreviousOutputsStep.execute(RemovePreviousOutputsStep.java:37)
	at org.gradle.internal.execution.steps.CancelExecutionStep.execute(CancelExecutionStep.java:41)
	at org.gradle.internal.execution.steps.TimeoutStep.executeWithoutTimeout(TimeoutStep.java:74)
	at org.gradle.internal.execution.steps.TimeoutStep.execute(TimeoutStep.java:55)
	at org.gradle.internal.execution.steps.CreateOutputsStep.execute(CreateOutputsStep.java:50)
	at org.gradle.internal.execution.steps.CreateOutputsStep.execute(CreateOutputsStep.java:28)
	at org.gradle.internal.execution.steps.CaptureStateAfterExecutionStep.executeDelegateBroadcastingChanges(CaptureStateAfterExecutionStep.java:100)
	at org.gradle.internal.execution.steps.CaptureStateAfterExecutionStep.execute(CaptureStateAfterExecutionStep.java:72)
	at org.gradle.internal.execution.steps.CaptureStateAfterExecutionStep.execute(CaptureStateAfterExecutionStep.java:50)
	at org.gradle.internal.execution.steps.ResolveInputChangesStep.execute(ResolveInputChangesStep.java:40)
	at org.gradle.internal.execution.steps.ResolveInputChangesStep.execute(ResolveInputChangesStep.java:29)
	at org.gradle.internal.execution.steps.BuildCacheStep.executeWithoutCache(BuildCacheStep.java:179)
	at org.gradle.internal.execution.steps.BuildCacheStep.lambda$execute$1(BuildCacheStep.java:70)
	at org.gradle.internal.Either$Right.fold(Either.java:175)
	at org.gradle.internal.execution.caching.CachingState.fold(CachingState.java:59)
	at org.gradle.internal.execution.steps.BuildCacheStep.execute(BuildCacheStep.java:68)
	at org.gradle.internal.execution.steps.BuildCacheStep.execute(BuildCacheStep.java:46)
	at org.gradle.internal.execution.steps.StoreExecutionStateStep.execute(StoreExecutionStateStep.java:36)
	at org.gradle.internal.execution.steps.StoreExecutionStateStep.execute(StoreExecutionStateStep.java:25)
	at org.gradle.internal.execution.steps.RecordOutputsStep.execute(RecordOutputsStep.java:36)
	at org.gradle.internal.execution.steps.RecordOutputsStep.execute(RecordOutputsStep.java:22)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.executeBecause(SkipUpToDateStep.java:91)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.lambda$execute$2(SkipUpToDateStep.java:55)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.execute(SkipUpToDateStep.java:55)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.execute(SkipUpToDateStep.java:37)
	at org.gradle.internal.execution.steps.ResolveChangesStep.execute(ResolveChangesStep.java:65)
	at org.gradle.internal.execution.steps.ResolveChangesStep.execute(ResolveChangesStep.java:36)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep.execute(MarkSnapshottingInputsFinishedStep.java:37)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep.execute(MarkSnapshottingInputsFinishedStep.java:27)
	at org.gradle.internal.execution.steps.ResolveCachingStateStep.execute(ResolveCachingStateStep.java:77)
	at org.gradle.internal.execution.steps.ResolveCachingStateStep.execute(ResolveCachingStateStep.java:38)
	at org.gradle.internal.execution.steps.ValidateStep.execute(ValidateStep.java:108)
	at org.gradle.internal.execution.steps.ValidateStep.execute(ValidateStep.java:55)
	at org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep.execute(CaptureStateBeforeExecutionStep.java:71)
	at org.gradle.internal.execution.steps.CaptureStateBeforeExecutionStep.execute(CaptureStateBeforeExecutionStep.java:45)
	at org.gradle.internal.execution.steps.SkipEmptyWorkStep.executeWithNonEmptySources(SkipEmptyWorkStep.java:177)
	at org.gradle.internal.execution.steps.SkipEmptyWorkStep.execute(SkipEmptyWorkStep.java:86)
	at org.gradle.internal.execution.steps.SkipEmptyWorkStep.execute(SkipEmptyWorkStep.java:53)
	at org.gradle.internal.execution.steps.RemoveUntrackedExecutionStateStep.execute(RemoveUntrackedExecutionStateStep.java:32)
	at org.gradle.internal.execution.steps.RemoveUntrackedExecutionStateStep.execute(RemoveUntrackedExecutionStateStep.java:21)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsStartedStep.execute(MarkSnapshottingInputsStartedStep.java:38)
	at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:36)
	at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:23)
	at org.gradle.internal.execution.steps.CleanupStaleOutputsStep.execute(CleanupStaleOutputsStep.java:75)
	at org.gradle.internal.execution.steps.CleanupStaleOutputsStep.execute(CleanupStaleOutputsStep.java:41)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.lambda$execute$2(ExecuteWorkBuildOperationFiringStep.java:66)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:66)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:38)
	at org.gradle.internal.execution.steps.AssignWorkspaceStep.lambda$execute$0(AssignWorkspaceStep.java:32)
	at org.gradle.api.internal.tasks.execution.TaskExecution$4.withWorkspace(TaskExecution.java:293)
	at org.gradle.internal.execution.steps.AssignWorkspaceStep.execute(AssignWorkspaceStep.java:30)
	at org.gradle.internal.execution.steps.AssignWorkspaceStep.execute(AssignWorkspaceStep.java:21)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:37)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:27)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:47)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:34)
	at org.gradle.internal.execution.impl.DefaultExecutionEngine$1.execute(DefaultExecutionEngine.java:64)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeIfValid(ExecuteActionsTaskExecuter.java:145)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:134)
	at org.gradle.api.internal.tasks.execution.FinalizePropertiesTaskExecuter.execute(FinalizePropertiesTaskExecuter.java:46)
	at org.gradle.api.internal.tasks.execution.ResolveTaskExecutionModeExecuter.execute(ResolveTaskExecutionModeExecuter.java:51)
	at org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter.execute(SkipTaskWithNoActionsExecuter.java:57)
	at org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter.execute(SkipOnlyIfTaskExecuter.java:74)
	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.executeTask(EventFiringTaskExecuter.java:77)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:55)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:52)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:199)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:157)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
	at org.gradle.internal.operations.DefaultBuildOperationExecutor.call(DefaultBuildOperationExecutor.java:78)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter.execute(EventFiringTaskExecuter.java:52)
	at org.gradle.execution.plan.LocalTaskNodeExecutor.execute(LocalTaskNodeExecutor.java:42)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:331)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:318)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.lambda$execute$0(DefaultTaskExecutionGraph.java:314)
	at org.gradle.internal.operations.CurrentBuildOperationRef.with(CurrentBuildOperationRef.java:80)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:314)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:303)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.execute(DefaultPlanExecutor.java:463)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.run(DefaultPlanExecutor.java:380)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:47)
Caused by: org.jetbrains.kotlin.gradle.tasks.CompilationErrorException: Compilation error. See log for more details
	at org.jetbrains.kotlin.gradle.tasks.TasksUtilsKt.throwExceptionIfCompilationFailed(tasksUtils.kt:20)
	at org.jetbrains.kotlin.compilerRunner.GradleKotlinCompilerWork.run(GradleKotlinCompilerWork.kt:141)
	at org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction.execute(GradleCompilerRunnerWithWorkers.kt:73)
	at org.gradle.workers.internal.DefaultWorkerServer.execute(DefaultWorkerServer.java:63)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1$1.create(NoIsolationWorkerFactory.java:66)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1$1.create(NoIsolationWorkerFactory.java:62)
	at org.gradle.internal.classloader.ClassLoaderUtils.executeInClassloader(ClassLoaderUtils.java:100)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1.lambda$execute$0(NoIsolationWorkerFactory.java:62)
	at org.gradle.workers.internal.AbstractWorker$1.call(AbstractWorker.java:44)
	at org.gradle.workers.internal.AbstractWorker$1.call(AbstractWorker.java:41)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:204)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:199)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:66)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:157)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:59)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:53)
	at org.gradle.internal.operations.DefaultBuildOperationExecutor.call(DefaultBuildOperationExecutor.java:78)
	at org.gradle.workers.internal.AbstractWorker.executeWrappedInBuildOperation(AbstractWorker.java:41)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1.execute(NoIsolationWorkerFactory.java:59)
	at org.gradle.workers.internal.DefaultWorkerExecutor.lambda$submitWork$0(DefaultWorkerExecutor.java:170)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.runExecution(DefaultConditionalExecutionQueue.java:187)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.access$700(DefaultConditionalExecutionQueue.java:120)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner$1.run(DefaultConditionalExecutionQueue.java:162)
	at org.gradle.internal.Factories$1.create(Factories.java:31)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:264)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:128)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:133)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.runBatch(DefaultConditionalExecutionQueue.java:157)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.run(DefaultConditionalExecutionQueue.java:126)
	... 2 more


BUILD FAILED in 2m 3s
Error: Process completed with exit code 1.
# Xử Lý Sự Cố & Lỗi Thực Tế (Troubleshooting)

Tài liệu này tổng hợp toàn bộ các sự cố kỹ thuật thực tế đã được phát hiện, điều tra và giải quyết trong suốt quá trình phát triển mã nguồn của dự án **LiteTube**.

---

## 1. Lỗi AAPT: `attribute android:layoutHeight not found`

### Hiện tượng & Nhật ký lỗi
Khi chạy `./gradlew assembleDebug` trên CI hoặc máy trạm (xem chi tiết tại `error.txt` dòng 61-68):
```text
Execution failed for task ':app:processDebugResources'.
> Android resource linking failed
  /layout/activity_main.xml:6: error: attribute android:layoutHeight not found.
  /layout/activity_main.xml:6: error: attribute android:layoutWidth not found.
  error: failed linking file resources.
```

### Nguyên nhân đã xác minh
Trong file XML layout Android, các thuộc tính layout bắt buộc phải viết theo chuẩn **snake_case** (`android:layout_width`, `android:layout_height`). Việc viết nhầm thành định dạng **camelCase** (`android:layoutWidth`, `android:layoutHeight`) khiến trình biên dịch tài nguyên AAPT2 không tìm thấy định nghĩa thuộc tính trong Android SDK.

### Cách khắc phục (Đã sửa tại commit `7153bd7`)
Mở `app/src/main/res/layout/activity_main.xml` và đổi tất cả các thuộc tính layout về đúng chuẩn:
```xml
android:layout_width="match_parent"
android:layout_height="match_parent"
```

---

## 2. PiP Bị Màn Hình Đen Nhưng Vẫn Có Tiếng (Black Screen on PiP)

### Hiện tượng
Khi người dùng chuyển sang chế độ Picture-in-Picture, cửa sổ PiP nổi lên chỉ hiển thị một màu đen kịt, nhưng âm thanh video vẫn phát bình thường. Khi chạm vào mở lại toàn màn hình thì video lại hiển thị bình thường.

### Nguyên nhân đã xác minh
Trong `MainActivity.kt`, nếu đặt `webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)`, WebView sẽ được render vào một bộ đệm ngoài màn hình (offscreen buffer). Vì toàn bộ Activity đã có cờ `android:hardwareAccelerated="true"` trong `AndroidManifest.xml`, việc ép thêm hardware layer ở cấp View khiến bộ điều phối cửa sổ của Android không cập nhật các khung hình mới vào bộ đệm khi kích thước view bị thu nhỏ đột ngột về khung PiP.

### Cách khắc phục
Đặt layer type về chế độ mặc định:
```kotlin
webView.setLayerType(View.LAYER_TYPE_NONE, null)
```

---

## 3. Nút Play/Pause Trên PiP Không Hoạt Động Trên Android 13/14

### Hiện tượng
Cửa sổ PiP hiện ra có nút Play và Pause, nhưng khi người dùng bấm vào các nút này trên điện thoại chạy Android 13 hoặc 14, không có phản hồi nào xảy ra.

### Nguyên nhân đã xác minh
Bắt đầu từ Android 13 (API 33) và bắt buộc trên Android 14 (API 34), khi đăng ký `BroadcastReceiver`, nhà phát triển phải chỉ định cờ `RECEIVER_EXPORTED` hoặc `RECEIVER_NOT_EXPORTED`.
Các nút `RemoteAction` trên khung PiP được phát lệnh từ tiến trình hệ điều hành (`com.android.systemui`). Nếu đăng ký bằng `RECEIVER_NOT_EXPORTED`, hệ thống Android sẽ chặn các broadcast này từ SystemUI để đảm bảo an ninh, khiến receiver của ứng dụng không bao giờ nhận được sự kiện.

### Cách khắc phục
Sử dụng cờ `ContextCompat.RECEIVER_EXPORTED`:
```kotlin
ContextCompat.registerReceiver(
    this,
    mediaActionReceiver,
    filter,
    ContextCompat.RECEIVER_EXPORTED
)
```

---

## 4. Văng Ứng Dụng (Crash) `IllegalArgumentException` Khi Vào PiP

### Hiện tượng
Ứng dụng bị văng đột ngột khi mở một số video định dạng đặc thù (video siêu rộng tỉ lệ 21:9 hoặc video định dạng ngắn YouTube Shorts dọc).

### Nguyên nhân đã xác minh
Hệ điều hành Android quy định nghiêm ngặt: Tỉ lệ khung hình của `PictureInPictureParams.Builder().setAspectRatio(...)` chỉ được phép nằm trong khoảng:
$$\frac{1}{2.39} \le \text{ratio} \le 2.39$$
Nếu truyền một tỉ lệ vượt ra ngoài khoảng này, hàm native của Android sẽ ném `IllegalArgumentException`.

### Cách khắc phục
Tạo hàm kẹp tỉ lệ an toàn:
```kotlin
private fun clampedPipAspectRatio(): Rational {
    val ratio = videoAspectRatio.toFloat()
    if (ratio.isNaN() || ratio <= 0f) return Rational(16, 9)
    val maxRatio = 2.39f
    val minRatio = 1f / 2.39f
    return when {
        ratio > maxRatio -> Rational(239, 100)
        ratio < minRatio -> Rational(100, 239)
        else -> videoAspectRatio
    }
}
```

---

## 5. Vi Phạm Chính Sách Bảo Mật Trusted Types CSP Khi Tiêm CSS

### Hiện tượng
Khi tiêm CSS ẩn quảng cáo vào `m.youtube.com`, trình duyệt báo lỗi console:
`This document requires 'TrustedHTML' assignment.` và từ chối nạp style.

### Nguyên nhân đã xác minh
Trang YouTube Mobile áp dụng chính sách CSP rất ngặt nghèo, chặn hoàn toàn việc gán chuỗi trực tiếp vào `style.innerHTML` hoặc `insertAdjacentHTML`.

### Cách khắc phục
Mã hóa CSS sang chuỗi Base64 và gán vào `style.textContent` thông qua `TextDecoder`:
```javascript
style.textContent = (window.TextDecoder ? new TextDecoder('utf-8').decode(bytes) : bin);
```
Thuộc tính `textContent` không thực thi phân tích cú pháp HTML nên hoàn toàn miễn nhiễm với chính sách TrustedHTML CSP.

---

## 6. YouTube Tự Dừng Video Khi Vào PiP (`PLAYER_VIEW_TOO_SMALL`)

### Hiện tượng
Khi Activity co nhỏ lại vào khung PiP, video chạy được 1 giây rồi tự động bị dừng lại.

### Nguyên nhân đã xác minh
Bộ mã nguồn web của YouTube có logic kiểm tra kích thước của trình phát: Nếu chiều cao hoặc chiều rộng của video nhỏ hơn ngưỡng quy định, script YouTube sẽ phát lệnh `video.pause()`.

### Cách khắc phục
Ghi đè `HTMLMediaElement.prototype.pause`: Chỉ cho phép lệnh dừng video nếu người dùng bấm trực tiếp vào nút tạm dừng hoặc nút Pause trên PiP Native. Mọi lệnh `pause` do YouTube tự gọi khi khung nhìn bị thu nhỏ đều bị scriptlet chặn lại.

---

## 7. Video Không Mở Được Tầng Nổi Vì YouTube SPA Router Bỏ Qua `shouldOverrideUrlLoading`

### Hiện tượng
Khi triển khai tính năng In-App MiniPlayer 2 tầng, người dùng bấm vào bất kỳ video nào trên trang chủ thì video vẫn mở thẳng trong WebView trang chủ, tầng phát nổi `playerPanel` không bao giờ được kích hoạt.

### Nguyên nhân đã xác minh
YouTube Mobile Web là Single Page Application (SPA). Khi người dùng bấm vào một video, router nội bộ của YouTube chặn sự kiện click và gọi `history.pushState(..., '/watch?v=...')`.
Android WebView **không kích hoạt callback `WebViewClient.shouldOverrideUrlLoading`** đối với các thay đổi URL nội bộ do `history.pushState` hoặc script SPA tạo ra.

### Cách khắc phục (Đã sửa tại commit `59747ff`)
Tiêm scriptlet `browseWatchInterceptorJs` vào Browse WebView:
1. Đăng ký listener tại **Capture Phase** (`useCapture = true`) trên `document` để đón đầu mọi click vào thẻ `<a>` chứa `/watch`.
2. Hook cả `history.pushState` và `history.replaceState`.
3. Khi phát hiện URL `/watch` (loại trừ `/shorts/`), gọi `e.preventDefault()`, `e.stopImmediatePropagation()` và chuyển quyền điều khiển sang Native thông qua `window.AndroidBridge.openWatchLayer(url)`.

---

## 8. Kẹt Nút Play/Pause & Ngắt Âm Thanh Khi Thoát PiP Phóng To Lại Ứng Dụng

### Hiện tượng
Khi video đang phát trong cửa sổ PiP, người dùng bấm phóng to trở lại ứng dụng thì nút Play/Pause bị kẹt (không thể bấm được hoặc hiển thị sai trạng thái), đôi khi video bị dừng tiếng cho đến khi người dùng tua video.

### Nguyên nhân đã xác minh
1. Quá trình resize khung nhìn đột ngột từ nhỏ lên toàn màn hình kích hoạt bẫy auto-pause của YouTube.
2. Trình phát Web component của YouTube rơi vào trạng thái ngủ đông controls khi thoát PiP.

### Cách khắc phục (Đã sửa tại `pip_controller.js`)
1. Cấp cờ bảo vệ `__braveProtectPlayUntil = Date.now() + 2500` chặn auto-pause trong 2.5 giây đầu sau khi phóng to.
2. Triển khai cơ chế **Micro-Seek Re-render Kick**: Gán lại `video.currentTime = video.currentTime` kết hợp dispatch sự kiện `seeking`, `seeked`, `timeupdate` và gọi `player.wakeUpControls()`, `player.updateDimensions()` để đánh thức ngay lập tức pipeline hiển thị và điều khiển của YouTube.
