/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.ikaddoura.optAV;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.av.robotaxi.scoring.TaxiFareConfigGroup;
import org.matsim.contrib.av.robotaxi.scoring.TaxiFareHandler;
import org.matsim.contrib.dvrp.data.FleetImpl;
import org.matsim.contrib.dvrp.data.file.VehicleReader;
import org.matsim.contrib.dvrp.optimizer.VrpOptimizer;
import org.matsim.contrib.dvrp.passenger.PassengerRequestCreator;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.trafficmonitoring.VrpTravelTimeModules;
import org.matsim.contrib.dvrp.vrpagent.VrpAgentLogic.DynActionCreator;
import org.matsim.contrib.noise.NoiseCalculationOnline;
import org.matsim.contrib.noise.NoiseConfigGroup;
import org.matsim.contrib.noise.data.NoiseContext;
import org.matsim.contrib.noise.utils.MergeNoiseCSVFile;
import org.matsim.contrib.noise.utils.ProcessNoiseImmissions;
import org.matsim.contrib.otfvis.OTFVisLiveModule;
import org.matsim.contrib.taxi.optimizer.DefaultTaxiOptimizerProvider;
import org.matsim.contrib.taxi.optimizer.TaxiOptimizer;
import org.matsim.contrib.taxi.passenger.TaxiRequestCreator;
import org.matsim.contrib.taxi.run.TaxiConfigConsistencyChecker;
import org.matsim.contrib.taxi.run.TaxiConfigGroup;
import org.matsim.contrib.taxi.run.TaxiModule;
import org.matsim.contrib.taxi.vrpagent.TaxiActionCreator;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vis.otfvis.OTFVisConfigGroup;

import playground.ikaddoura.analysis.detailedPersonTripAnalysis.PersonTripNoiseAnalysisRun;
import playground.ikaddoura.analysis.dynamicLinkDemand.DynamicLinkDemandAnalysisRun;
import playground.ikaddoura.analysis.linkDemand.LinkDemandAnalysisRun;
import playground.ikaddoura.decongestion.DecongestionConfigGroup;
import playground.ikaddoura.decongestion.DecongestionConfigGroup.TollingApproach;
import playground.ikaddoura.decongestion.DecongestionControlerListener;
import playground.ikaddoura.decongestion.data.DecongestionInfo;
import playground.ikaddoura.decongestion.tollSetting.DecongestionTollSetting;
import playground.ikaddoura.decongestion.tollSetting.DecongestionTollingPID;
import playground.ikaddoura.moneyTravelDisutility.MoneyEventAnalysis;
import playground.ikaddoura.moneyTravelDisutility.MoneyTimeDistanceTravelDisutilityFactory;

/**
* @author ikaddoura
*/

public class RunBerlinMinExtCostAV {

	private static final Logger log = Logger.getLogger(RunBerlinMinExtCostAV.class);

	private static String configFile;
	private static String outputDirectory;
	private static boolean otfvis;
	
	public static void main(String[] args) {
		if (args.length > 0) {
			
			configFile = args[0];		
			log.info("configFile: "+ configFile);
			
			outputDirectory = args[1];
			log.info("outputDirectory: "+ outputDirectory);
			
			otfvis = false;
			
		} else {
			configFile = "/Users/ihab/Documents/workspace/runs-svn/optAV/input/config_be_10pct_test.xml";
			outputDirectory = "/Users/ihab/Documents/workspace/runs-svn/optAV/output/minExtCostAV/";
			otfvis = false;
		}
		
		RunBerlinMinExtCostAV runBerlinOptAV = new RunBerlinMinExtCostAV();
		runBerlinOptAV.run();
	}

	private void run() {
		
		Config config = ConfigUtils.loadConfig(configFile,
				new TaxiConfigGroup(),
				new TaxiFareConfigGroup(),
				new OTFVisConfigGroup(),
				new NoiseConfigGroup());
		
		config.controler().setOutputDirectory(outputDirectory);
		
		TaxiConfigGroup taxiCfg = TaxiConfigGroup.get(config);
		config.addConfigConsistencyChecker(new TaxiConfigConsistencyChecker());
		config.checkConsistency();
		
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Controler controler = new Controler(scenario);
				
		// #############################
		// noise pricing
		// #############################

		controler.addControlerListener(new NoiseCalculationOnline(new NoiseContext(controler.getScenario())));
		
		// #############################
		// congestion pricing
		// #############################

		final DecongestionConfigGroup decongestionSettings = new DecongestionConfigGroup();
		decongestionSettings.setTOLLING_APPROACH(TollingApproach.PID);
		decongestionSettings.setKp(2 * 12./3600.);
		decongestionSettings.setKi(0.);
		decongestionSettings.setKd(0.);
		decongestionSettings.setMsa(true);
		decongestionSettings.setRUN_FINAL_ANALYSIS(false);
		decongestionSettings.setWRITE_LINK_INFO_CHARTS(false);
		
		final DecongestionInfo info = new DecongestionInfo(controler.getScenario(), decongestionSettings);
		final DecongestionTollSetting tollSettingApproach = new DecongestionTollingPID(info);	
		
		final DecongestionControlerListener decongestion = new DecongestionControlerListener(info, tollSettingApproach);		
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				this.addControlerListenerBinding().toInstance(decongestion);
			}
		});
		
		// #############################
		// taxi
		// #############################

		FleetImpl fleet = new FleetImpl();
		new VehicleReader(scenario.getNetwork(), fleet).readFile(taxiCfg.getTaxisFileUrl(config.getContext()).getFile());
		
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				addEventHandlerBinding().to(TaxiFareHandler.class).asEagerSingleton();
			}
		});
		controler.addOverridingModule(new TaxiModule());
		controler.addOverridingModule(VrpTravelTimeModules.createTravelTimeEstimatorModule(0.05));
        controler.addOverridingModule(new DvrpModule(TaxiModule.TAXI_MODE, fleet, new com.google.inject.AbstractModule() {
			@Override
			protected void configure() {
				bind(TaxiOptimizer.class).toProvider(DefaultTaxiOptimizerProvider.class).asEagerSingleton();
				bind(VrpOptimizer.class).to(TaxiOptimizer.class);
				bind(DynActionCreator.class).to(TaxiActionCreator.class).asEagerSingleton();
				bind(PassengerRequestCreator.class).to(TaxiRequestCreator.class).asEagerSingleton();
			}
		}, TaxiOptimizer.class));

		final MoneyTimeDistanceTravelDisutilityFactory dvrpTravelDisutilityFactory = new MoneyTimeDistanceTravelDisutilityFactory(null);

		controler.addOverridingModule(new AbstractModule(){
			@Override
			public void install() {
												
				// travel disutility factory for DVRP
				addTravelDisutilityFactoryBinding(VrpTravelTimeModules.DVRP_ESTIMATED)
						.toInstance(dvrpTravelDisutilityFactory);
				
				this.bind(MoneyEventAnalysis.class).asEagerSingleton();
				this.addControlerListenerBinding().to(MoneyEventAnalysis.class);
				this.addEventHandlerBinding().to(MoneyEventAnalysis.class);
			}
		}); 

		// #############################
		// run
		// #############################
		
		if (otfvis) controler.addOverridingModule(new OTFVisLiveModule());	
        controler.getConfig().controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		controler.run();
		
		// #############################
		// analysis
		// #############################
		
		String immissionsDir = controler.getConfig().controler().getOutputDirectory() + "/ITERS/it." + controler.getConfig().controler().getLastIteration() + "/immissions/";
		String receiverPointsFile = controler.getConfig().controler().getOutputDirectory() + "/receiverPoints/receiverPoints.csv";
		
		NoiseConfigGroup noiseParams = (NoiseConfigGroup) controler.getConfig().getModules().get(NoiseConfigGroup.GROUP_NAME);

		ProcessNoiseImmissions processNoiseImmissions = new ProcessNoiseImmissions(immissionsDir, receiverPointsFile, noiseParams.getReceiverPointGap());
		processNoiseImmissions.run();
		
		final String[] labels = { "immission", "consideredAgentUnits" , "damages_receiverPoint" };
		final String[] workingDirectories = { controler.getConfig().controler().getOutputDirectory() + "/ITERS/it." + controler.getConfig().controler().getLastIteration() + "/immissions/" , controler.getConfig().controler().getOutputDirectory() + "/ITERS/it." + controler.getConfig().controler().getLastIteration() + "/consideredAgentUnits/" , controler.getConfig().controler().getOutputDirectory() + "/ITERS/it." + controler.getConfig().controler().getLastIteration()  + "/damages_receiverPoint/" };

		MergeNoiseCSVFile merger = new MergeNoiseCSVFile() ;
		merger.setReceiverPointsFile(receiverPointsFile);
		merger.setOutputDirectory(controler.getConfig().controler().getOutputDirectory() + "/ITERS/it." + controler.getConfig().controler().getLastIteration() + "/");
		merger.setTimeBinSize(noiseParams.getTimeBinSizeNoiseComputation());
		merger.setWorkingDirectory(workingDirectories);
		merger.setLabel(labels);
		merger.run();
		
		PersonTripNoiseAnalysisRun analysis1 = new PersonTripNoiseAnalysisRun(controler.getConfig().controler().getOutputDirectory());
		analysis1.run();
		
		DynamicLinkDemandAnalysisRun analysis2 = new DynamicLinkDemandAnalysisRun(controler.getConfig().controler().getOutputDirectory());
		analysis2.run();
		
		LinkDemandAnalysisRun analysis3 = new LinkDemandAnalysisRun(controler.getConfig().controler().getOutputDirectory());
		analysis3.run();
	}
}
