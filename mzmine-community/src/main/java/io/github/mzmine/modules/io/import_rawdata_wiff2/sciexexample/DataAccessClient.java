/*
 * Copyright (c) 2004-2025 The mzmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.io.import_rawdata_wiff2.sciexexample;

import io.github.mzmine.modules.io.import_rawdata_wiff2.api.DataProviderGrpc;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.Experiment;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.GetExperimentsRequest;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.GetMrmXicRequest;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.GetSpectraRequest;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.GetWavelengthSpectraRequest;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.ListSamplesRequest;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.MrmXic;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.Spectrum;
import io.github.mzmine.modules.io.import_rawdata_wiff2.api.TimeRange;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

//public class DataAccessClient {
//
//  // Constructor of DataAccessClient that will create a communication
//  // channel for client to communicate with the server.
//  // ** DataAcessClient.java encapsulates the operations required to
//  // make a call to the server
//
//  private ManagedChannel channel;
//
//  public DataAccessClient(String host, int port) {
//    this(ManagedChannelBuilder.forAddress(host, port).usePlaintext()
//        .keepAliveTimeout(90, TimeUnit.SECONDS).maxInboundMessageSize(1024 * 1024 * 5)
//        .maxRetryAttempts(3).build());
//  }
//
//  public DataAccessClient(ManagedChannel channel) {
//    this.channel = channel;
//
//
//  }
//
//  public static void main(String[] args) throws ExecutionException, InterruptedException {
//    if (ShouldShowHelp(args)) {
//      ShowUsage();
//      return;
//    }
//    Parameter executionParameter = GetParameters(args);
//    if (!executionParameter.Parsed) {
//      return;
//    }
//    System.out.println(
//        String.format("File %s is going to be usedfor testing.", executionParameter.Path));
//    int portAsInteger = Integer.parseInt(executionParameter.Port);
//    DataAccessClient _client = new DataAccessClient(executionParameter.Host,
//        portAsInteger); // See below the end of method for more details
//    _client.ProcessSamples(executionParameter);
//    if (null != _client) {
//      _client.shutdown();
//    }
//  }
//
//  public void ProcessSamples(Parameter params) {
//    var samplesRequestBuilder = ListSamplesRequest.newBuilder()
//        .setAbsolutePathToWiffFile(params.Path).setSkipCorrupted(true).build();
//    var dataProviderClient = DataProviderGrpc.newBlockingStub(this.channel);
//    var samplesDescriptions = dataProviderClient.getSamplesDescriptions(samplesRequestBuilder);
//// A sample may contain hundreds of samples and each sample may
//// contain hundreds of experiments and spectra.
//    while (samplesDescriptions.hasNext()) {
//      var sampleRead = samplesDescriptions.next();
//      System.out.println("Sample:");
//      System.out.println(sampleRead);
//      ProcessExperiments(dataProviderClient, sampleRead.getId(), params.TimeRangeStart,
//          params.TimeRangeEnd, params.ConvertToCentroid);
//      var spectrumMode = sampleRead.getSupportsSpectrumModeWavelenghtChromatogram();
//      var channelMode = sampleRead.getSupportsChannelModeWavelenghtChromatogram();
//      if (sampleRead.getSupportsSpectrumModeWavelenghtChromatogram()) {
//        ProcessWavelengthSpectra(dataProviderClient, sampleRead.getId(), params.TimeRangeStart,
//            params.TimeRangeEnd, spectrumMode, channelMode);
//      }
//      var sourcesList = sampleRead.getSourcesList();
//      CloseFile(dataProviderClient, sourcesList.get(0));
//    }
//  }
//
//  private void CloseFile(DataProviderGrpc.DataProviderBlockingStub dataProviderClient,
//      SourceFile source) {
//// just pass the file descriptor back to the server. The
//// descriptor was provided as part of the get samples
//// response
//    dataProviderClient.closeFile(source);
//  }
//
//  private void ProcessExperiments(DataProviderGrpc.DataProviderBlockingStub dataProviderClient,
//      String sampleId, double timeRangeStart, double timeRangeEnd, boolean convertToCentroid /* spectra in centroid format is
//achieved by setting convertToCentroid to true*/) {
//    var experimentsRequest = GetExperimentsRequest.newBuilder().setSampleId(sampleId)
///* A value of false can be passed, or the ReadDetailedParameters
//property set can be omitted if detailed experiment parameters
//aren't desired. */.setReadDetailedParameters(true).build();
//    var experimentsRead = dataProviderClient.getExperiments(experimentsRequest);
//// there could be up to hundreds of experiments in a sample.
//// and each experimentand each experiment may contains
//// hundreds of spectra
//    while (experimentsRead.hasNext()) {
//      Experiment experimentRead = experimentsRead.next();
//      /* get next experiment */
//      System.out.println("Experiment:");
//
//      System.out.println(experimentRead);
//      ProcessSpectra(dataProviderClient, sampleId, experimentRead.getId(), timeRangeStart,
//          timeRangeEnd, convertToCentroid);
//    }
//  }
//
//  private void ProcessSpectra(DataProviderGrpc.DataProviderBlockingStub dataProviderClient,
//      String sampleId, String experimentId, double timeRangeStart, double timeRangeEnd,
//      boolean convertToCentroid) {
//// this is how window is provided. if all data is required start
//// from 0 to max
//    var timeRange = TimeRange.newBuilder()
//        .setStart(timeRangeStart) /* set to 0 (start from begin)*/.setEnd(
//            timeRangeEnd) /* set to double.MAX (all spectra))*/.build();
//    var spectraRequest = GetSpectraRequest.newBuilder().setSampleId(sampleId)
//        .setExperimentId(experimentId).setRange(timeRange).setConvertToCentroid(convertToCentroid) /* spectra in
//centroid format is achieve by setting convertToCentroid to
//true*/.build();
//    var spectraRead = dataProviderClient.getSpectra(spectraRequest);
//// number of spectra per experiment could go up to thousands for
//// a given experiment.
//    while (spectraRead.hasNext()) {
//      Spectrum spectrumRead = spectraRead.next();
//      // get and Show attributes for the first index
//      var attributes = spectrumRead.getData(0).getAttributesList();
//      ShowAttributes(attributes);
//      var indices = GetIndices(attributes, "MS:1000514");
//      ShowData(spectrumRead.getData(indices.XIndex), spectrumRead.getData(indices.YIndex), indices);
//    }
//  }
//
//
//  private void ProcessSpectra(DataProviderGrpc.DataProviderBlockingStub dataProviderClient,
//      String sampleId, String experimentId, double timeRangeStart, double timeRangeEnd,
//      boolean convertToCentroid) {
//    var timeRange = TimeRange.newBuilder().setStart(timeRangeStart) /* set to 0 to start */.setEnd(
//        timeRangeEnd)
///* set to double.MAX to include all window*/.build();
//    var spectraRequest = GetSpectraRequest.newBuilder().setSampleId(sampleId)
//        .setExperimentId(experimentId).setRange(timeRange).setConvertToCentroid(true).build();
//  }
//
//  private void ProcessWavelengthSpectra(
//      DataProviderGrpc.DataProviderBlockingStub dataProviderClient, String sampleId,
//      double timeRangeStart, /* start = 0 */
//      double timeRangeEnd, /* end = double.MAX */
//      boolean spectraMode, boolean channelMode) {
//    var timeRange = TimeRange.newBuilder().setStart(timeRangeStart).setEnd(timeRangeEnd).build();
//    var wavelengthSpectraRequest = GetWavelengthSpectraRequest.newBuilder().setSampleId(sampleId)
//        .setRange(timeRange).setIsRequestingChannelModeData(channelMode).build();
//    String spectrumType = "";
//    if (spectraMode) {
//      spectrumType = "Spectrum mode wavelength spectrum id: ";
//    } else if (channelMode) {
//      spectrumType = "Channel mode wavelength spectrum id: ";
//    }
//    Iterator<WavelengthSpectrum> waveLengthSpectrumRead = dataProviderClient.getWavelengthSpectra(
//        wavelengthSpectraRequest);
//    while (waveLengthSpectrumRead.hasNext()) {
//      var wavelengthRead = waveLengthSpectrumRead.next();
//      System.out.println("WaveLengthSpectrum:");
//      System.out.println(spectrumType + wavelengthRead.getId());
//      var attributes = wavelengthRead.getData(0).getAttributesList();
//      ShowAttributes(attributes);
//      if (spectraMode) {
//// Get the X (retention time) and Y (absorbance) values.
//        var indices = GetIndices(attributes, "MS:1000617");
//        ShowData(wavelengthRead.getData(indices.XIndex), wavelengthRead.getData(indices.YIndex),
//            indices);
//      }
//      if (channelMode) {
//// Get the X (channel index) and Y (absorbance) values.
//// Note that the channel index is an integer.
//        var indices = GetIndices(attributes, "MS:1000617");
//        int[] xs = GetCoordinatesAsInt(wavelengthRead.getData(indices.XIndex).getValues());
//        var ys = GetCoordinatesAsInt(wavelengthRead.getData(indices.YIndex).getValues());
//      }
//    }
//  }
//
//  private void ProcessMrmXics(DataProviderGrpc.DataProviderBlockingStub dataProviderClient,
//      String sampleId, String experimentId, double timeRangeStart, double timeRangeEnd,
//      int[] massIndexes) {
//    var timeRange = TimeRange.newBuilder()
//        .setStart(timeRangeStart) /* set to 0 (start from begin)*/.setEnd(
//            timeRangeEnd) /* set to double.MAX (all XICs))*/.build();
//    var requestBuilder = GetMrmXicRequest.newBuilder();
//    requestBuilder.setSampleId(sampleId);
//    requestBuilder.setExperimentId(experimentId);
//    requestBuilder.setTimeRange(timeRange);
//    for (int i = 0; i < massIndexes.length; i++) {
//      requestBuilder.addMassIndexes(massIndexes[i]);
//    }
//    var mrmXicRequest = requestBuilder.build();
//    var mrmXicReader = dataProviderClient.getMrmXics(mrmXicRequest);
//    while (mrmXicReader.hasNext()) {
//      MrmXic mrmXic = mrmXicReader.next();
//      System.out.println("Sample Id: " + mrmXic.getSampleId());
//      System.out.println("Experiment Id: " + mrmXic.getExperimentId());
//      System.out.println("Cycle Range: Start: " + mrmXic.getCycleRange().getStart() + ", End: "
//          + mrmXic.getCycleRange().getEnd());
//      System.out.println("Time Range: Start: " + mrmXic.getTimeRange().getStart() + ", End: "
//          + mrmXic.getTimeRange().getEnd());
//      System.out.println("Mass Index: " + mrmXic.getMassIndex());
//      for (var i = 0; i < mrmXic.getXValuesCount(); ++i) {
//        System.out.println("X: " + mrmXic.getXValues(i) + ", Y: " + mrmXic.getYValues(i));
//      }
//      System.out.println(System.getProperty("line.separator"));
//    }
//  }
//}
