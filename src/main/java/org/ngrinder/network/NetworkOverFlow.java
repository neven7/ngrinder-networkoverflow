package org.ngrinder.network;

import net.grinder.common.processidentity.AgentIdentity;
import net.grinder.statistics.ImmutableStatisticsSet;
import net.grinder.statistics.StatisticsIndexMap;
import net.grinder.statistics.StatisticsIndexMap.LongIndex;
import net.grinder.util.UnitUtils;
import org.apache.commons.lang.StringUtils;
import org.ngrinder.extension.OnTestSamplingRunnable;
import org.ngrinder.model.AgentInfo;
import org.ngrinder.model.PerfTest;
import org.ngrinder.model.Status;
import org.ngrinder.service.IAgentManagerService;
import org.ngrinder.service.IConfig;
import org.ngrinder.service.IPerfTestService;
import org.ngrinder.service.ISingleConsole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Network overflow plugin.
 * This plugin blocks test running which causes the network overflow by the large test.
 *
 * @author JunHo Yoon
 * @since 3.3
 */
public class NetworkOverFlow implements OnTestSamplingRunnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkOverFlow.class);
    private final IConfig config;
    private final IAgentManagerService agentManagerService;
    private long limit;

    private static final String PROP_NETWORK_OVERFLOW_PERTEST_LIMIT = "plugin.networkoverflow.pertest.limit";
    private static final int PROP_NETWORK_OVERFLOW_PERTEST_LIMIT_DEFAULT = 128;

    public NetworkOverFlow(IConfig config, IAgentManagerService agentManagerService) {
        this.config = config;
        this.agentManagerService = agentManagerService;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.ngrinder.extension.OnTestSamplingRunnable#startSampling(org.ngrinder.service.ISingleConsole
     * , org.ngrinder.model.PerfTest, org.ngrinder.service.IPerfTestService)
     */
    @Override
    public void startSampling(ISingleConsole singleConsole, PerfTest perfTest, IPerfTestService perfTestService) {
        List<AgentIdentity> allAttachedAgents = singleConsole.getAllAttachedAgents();
        int consolePort = singleConsole.getConsolePort();
        int userSpecificAgentCount = 0;
        for (AgentInfo each : getLocalAgents()) {
            if (each.getPort() == consolePort && StringUtils.contains(each.getRegion(), "owned")) {
                userSpecificAgentCount++;
            }
        }
        long configuredLimit = getLimit();
        int totalAgentSize = allAttachedAgents.size();
        int sharedAgent = (totalAgentSize - userSpecificAgentCount);
        limit = sharedAgent == 0 ? Long.MAX_VALUE
                : (long) (configuredLimit / (((float) sharedAgent) / totalAgentSize));

    }

    int getLimit() {
        return this.config.getControllerProperties().getPropertyInt(PROP_NETWORK_OVERFLOW_PERTEST_LIMIT, PROP_NETWORK_OVERFLOW_PERTEST_LIMIT_DEFAULT) * 1024 * 1024;
    }

    List<AgentInfo> getLocalAgents() {
        return agentManagerService.getLocalAgents();
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.ngrinder.extension.OnTestSamplingRunnable#sampling(org.ngrinder.service.ISingleConsole,
     * org.ngrinder.model.PerfTest, org.ngrinder.service.IPerfTestService,
     * net.grinder.statistics.ImmutableStatisticsSet, net.grinder.statistics.ImmutableStatisticsSet)
     */
    @Override
    public void sampling(ISingleConsole singleConsole, PerfTest perfTest, IPerfTestService perfTestService,
                         ImmutableStatisticsSet intervalStatistics, ImmutableStatisticsSet cumulativeStatistics) {
        LongIndex longIndex = singleConsole.getStatisticsIndexMap().getLongIndex(
                StatisticsIndexMap.HTTP_PLUGIN_RESPONSE_LENGTH_KEY);
        Long byteSize = intervalStatistics.getValue(longIndex);
        if (byteSize > this.limit) {
            if (perfTest.getStatus() != Status.ABNORMAL_TESTING) {
                String message = String.format("Too much traffic on this test. Stop by force.\n"
                        + "- LIMIT : %s - SENT :%s", UnitUtils.byteCountToDisplaySize(this.limit),
                        UnitUtils.byteCountToDisplaySize(byteSize));
                LOGGER.info(message);
                LOGGER.info("Stop the test {} by force", perfTest.getTestIdentifier());
                perfTestService.markStatusAndProgress(perfTest, Status.ABNORMAL_TESTING, message);
            }
        }


    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.ngrinder.extension.OnTestSamplingRunnable#endSampling(org.ngrinder.service.ISingleConsole
     * , org.ngrinder.model.PerfTest, org.ngrinder.service.IPerfTestService)
     */
    @Override
    public void endSampling(ISingleConsole singleConsole, PerfTest perfTest, IPerfTestService perfTestService) {
    }

}
