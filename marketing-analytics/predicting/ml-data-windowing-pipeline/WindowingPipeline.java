// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.corp.gtech.ads.datacatalyst.components.mldatawindowingpipeline;

import com.google.corp.gtech.ads.datacatalyst.components.mldatawindowingpipeline.model.LookbackWindow;
import com.google.corp.gtech.ads.datacatalyst.components.mldatawindowingpipeline.model.Session;
import com.google.corp.gtech.ads.datacatalyst.components.mldatawindowingpipeline.transform.MapSortedSessionsIntoSessionLookbackWindows;
import com.google.corp.gtech.ads.datacatalyst.components.mldatawindowingpipeline.transform.MapSortedSessionsIntoSlidingLookbackWindows;
import com.google.corp.gtech.ads.datacatalyst.components.mldatawindowingpipeline.transform.MapUserIdToSession;
import com.google.corp.gtech.ads.datacatalyst.components.mldatawindowingpipeline.transform.SortSessionsByTime;
import java.util.List;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.AvroIO;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.GroupByKey;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;

/**
 * Outputs a collection of sliding and session-based LookbackWindows for each user. For both
 * collections, no LookbackWindow is output if it is empty. Also, if getStopOnFirstPositiveLabel
 * is true, then no LookbackWindows are output after the first one that has a positive label.
 *
 * Sliding LookbackWindows: For each user, outputs a LookbackWindow beginning at startTime and
 * moving forward by slideTimeInSeconds until the endTime.
 *
 * Session-based LookbackWindows: For each Session, one LookbackWindow is generated with the given
 * Session as the last in the window.
 */
public class WindowingPipeline {
  public static void main(String[] args) {
    WindowingPipelineOptions options =
        PipelineOptionsFactory.fromArgs(args).withValidation().as(WindowingPipelineOptions.class);
    Pipeline pipeline = Pipeline.create(options);

    PCollection<KV<String, List<Session>>> userToSortedSessionList = pipeline
        .apply(AvroIO.read(Session.class).from(options.getInputAvroSessionsLocation()))
        .apply("MapUserIdToSession", ParDo.of(new MapUserIdToSession()))
        .apply(GroupByKey.<String, Session>create())
        .apply("SortSessionsByTime", ParDo.of(new SortSessionsByTime()));

    // Output the Sliding LookbackWindows.
    userToSortedSessionList
        .apply("MapSortedSessionsIntoSlidingLookbackWindows", ParDo.of(
            new MapSortedSessionsIntoSlidingLookbackWindows(
                options.getStartDate(),
                options.getEndDate(),
                options.getLookbackGapInSeconds(),
                options.getWindowTimeInSeconds(),
                options.getSlideTimeInSeconds(),
                options.getMinimumLookaheadTimeInSeconds(),
                options.getMaximumLookaheadTimeInSeconds(),
                options.getStopOnFirstPositiveLabel())))
       .apply(
           AvroIO.write(LookbackWindow.class)
               .to(options.getOutputSlidingWindowAvroPrefix()).withSuffix(".avro"));

    // Output the Session-based LookbackWindows.
    userToSortedSessionList
        .apply("MapSortedSessionsIntoSessionLookbackWindows", ParDo.of(
            new MapSortedSessionsIntoSessionLookbackWindows(
                options.getStartDate(),
                options.getEndDate(),
                options.getLookbackGapInSeconds(),
                options.getWindowTimeInSeconds(),
                options.getMinimumLookaheadTimeInSeconds(),
                options.getMaximumLookaheadTimeInSeconds(),
                options.getStopOnFirstPositiveLabel())))
        .apply(
            AvroIO.write(LookbackWindow.class)
                 .to(options.getOutputSessionBasedWindowAvroPrefix()).withSuffix(".avro"));
    pipeline.run();
  }
}
