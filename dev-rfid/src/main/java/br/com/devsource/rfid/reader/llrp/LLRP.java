package br.com.devsource.rfid.reader.llrp;

import java.util.List;
import java.util.concurrent.TimeoutException;

import org.llrp.ltk.generated.enumerations.AISpecStopTriggerType;
import org.llrp.ltk.generated.enumerations.AirProtocols;
import org.llrp.ltk.generated.enumerations.ROReportTriggerType;
import org.llrp.ltk.generated.enumerations.ROSpecStartTriggerType;
import org.llrp.ltk.generated.enumerations.ROSpecState;
import org.llrp.ltk.generated.enumerations.ROSpecStopTriggerType;
import org.llrp.ltk.generated.interfaces.AirProtocolEPCMemorySelector;
import org.llrp.ltk.generated.messages.ADD_ROSPEC;
import org.llrp.ltk.generated.messages.DELETE_ROSPEC;
import org.llrp.ltk.generated.messages.ENABLE_ROSPEC;
import org.llrp.ltk.generated.messages.RO_ACCESS_REPORT;
import org.llrp.ltk.generated.messages.SET_READER_CONFIG;
import org.llrp.ltk.generated.messages.START_ROSPEC;
import org.llrp.ltk.generated.messages.STOP_ROSPEC;
import org.llrp.ltk.generated.parameters.AISpec;
import org.llrp.ltk.generated.parameters.AISpecStopTrigger;
import org.llrp.ltk.generated.parameters.AntennaConfiguration;
import org.llrp.ltk.generated.parameters.C1G2EPCMemorySelector;
import org.llrp.ltk.generated.parameters.EPC_96;
import org.llrp.ltk.generated.parameters.InventoryParameterSpec;
import org.llrp.ltk.generated.parameters.RFReceiver;
import org.llrp.ltk.generated.parameters.RFTransmitter;
import org.llrp.ltk.generated.parameters.ROBoundarySpec;
import org.llrp.ltk.generated.parameters.ROReportSpec;
import org.llrp.ltk.generated.parameters.ROSpec;
import org.llrp.ltk.generated.parameters.ROSpecStartTrigger;
import org.llrp.ltk.generated.parameters.ROSpecStopTrigger;
import org.llrp.ltk.generated.parameters.TagReportContentSelector;
import org.llrp.ltk.generated.parameters.TagReportData;
import org.llrp.ltk.net.LLRPConnectionAttemptFailedException;
import org.llrp.ltk.net.LLRPConnector;
import org.llrp.ltk.net.LLRPEndpoint;
import org.llrp.ltk.types.Bit;
import org.llrp.ltk.types.LLRPMessage;
import org.llrp.ltk.types.UnsignedByte;
import org.llrp.ltk.types.UnsignedInteger;
import org.llrp.ltk.types.UnsignedShort;
import org.llrp.ltk.types.UnsignedShortArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.devsource.rfid.AntennaConfig;
import br.com.devsource.rfid.ReaderConfig;
import br.com.devsource.rfid.ReaderException;
import br.com.devsource.rfid.reader.AbstractRfid;
import br.com.devsource.rfid.tag.Tag;

/**
 * @author Guilherme Pacheco
 */
public final class LLRP extends AbstractRfid implements LLRPEndpoint {

  private static final int TIME_OUT = 5000;
  private final LLRPConnector connector;
  private final UnsignedInteger rospecId = new UnsignedInteger(101);

  private static final Bit NO = new Bit(0);
  private static final Bit YES = new Bit(1);

  private static final Logger LOGGER = LoggerFactory.getLogger(LLRP.class);

  public LLRP(ReaderConfig leitor) {
    super(leitor);
    if (leitor.getPort() == 0) {
      connector = new LLRPConnector(this, leitor.getHostName());
    } else {
      connector = new LLRPConnector(this, leitor.getHostName(), leitor.getPort());
    }
  }

  @Override
  public void connect() {
    try {
      if (!isConnected()) {
        LOGGER.info("Iniciando conexão com leitor...");
        connector.connect(2000);
        setConnected(true);
        LOGGER.info("Conexão com leitor iniciada: " + leitor().toString());
      }
    } catch (LLRPConnectionAttemptFailedException ex) {
      throw new ReaderException("Erro de conexão com leitor", ex);
    }
  }

  @Override
  public void disconect() {
    if (isConnected()) {
      connector.disconnect();
      setConnected(false);
    }
  }

  private void enableROSpec() throws TimeoutException {
    ENABLE_ROSPEC rospec = new ENABLE_ROSPEC();
    rospec.setROSpecID(rospecId);
    transact(rospec);
  }

  private void deleteROSpec() throws TimeoutException {
    DELETE_ROSPEC rospec = new DELETE_ROSPEC();
    rospec.setROSpecID(rospecId);
    transact(rospec);
  }

  private UnsignedShortArray getIDAntennas() {
    UnsignedShortArray array = new UnsignedShortArray();
    leitor().getActiviesAntennas().forEach(
      antena -> array.add(new UnsignedShort(antena.getNumber())));
    return array;
  }

  private LLRPMessage transact(LLRPMessage message) throws TimeoutException {
    return connector.transact(message, TIME_OUT);
  }

  private ROReportSpec getReportSpec() {
    ROReportSpec reportSpec = new ROReportSpec();
    reportSpec.setROReportTrigger(new ROReportTriggerType(
      ROReportTriggerType.Upon_N_Tags_Or_End_Of_ROSpec));
    reportSpec.setN(new UnsignedShort(1));
    reportSpec.setTagReportContentSelector(getTagReportSelector());
    return reportSpec;
  }

  private void addROSpec() throws TimeoutException {
    ROSpec roSpec = new ROSpec();
    roSpec.setROSpecID(rospecId);
    roSpec.setPriority(new UnsignedByte(0));
    roSpec.setCurrentState(new ROSpecState(ROSpecState.Disabled));
    roSpec.setROBoundarySpec(getBoundarySpec());
    roSpec.addToSpecParameterList(getAiSpec());
    roSpec.setROReportSpec(getReportSpec());
    ADD_ROSPEC message = new ADD_ROSPEC();
    message.setROSpec(roSpec);
    transact(message);
  }

  private TagReportContentSelector getTagReportSelector() {
    TagReportContentSelector selector = new TagReportContentSelector();
    selector.setEnableAccessSpecID(YES);
    selector.setEnableAntennaID(YES);
    selector.setEnableChannelIndex(NO);
    selector.setEnableFirstSeenTimestamp(NO);
    selector.setEnableInventoryParameterSpecID(YES);
    selector.setEnableLastSeenTimestamp(NO);
    selector.setEnablePeakRSSI(NO);
    selector.setEnableROSpecID(NO);
    selector.setEnableSpecIndex(NO);
    selector.setEnableTagSeenCount(NO);
    List<AirProtocolEPCMemorySelector> airProtocolList =
        selector.getAirProtocolEPCMemorySelectorList();
    C1G2EPCMemorySelector memorySelector = new C1G2EPCMemorySelector();
    memorySelector.setEnableCRC(NO);
    memorySelector.setEnablePCBits(NO);
    airProtocolList.add(memorySelector);
    selector.setAirProtocolEPCMemorySelectorList(airProtocolList);
    return selector;
  }

  private AISpec getAiSpec() {
    AISpec aiSpec = new AISpec();
    AISpecStopTrigger aiSpecStopTrigger = new AISpecStopTrigger();
    aiSpecStopTrigger
      .setAISpecStopTriggerType(new AISpecStopTriggerType(AISpecStopTriggerType.Null));
    aiSpecStopTrigger.setDurationTrigger(new UnsignedInteger(0));
    aiSpec.setAISpecStopTrigger(aiSpecStopTrigger);
    aiSpec.setAntennaIDs(getIDAntennas());
    aiSpec.addToInventoryParameterSpecList(getInventoryParameter());
    return aiSpec;
  }

  private InventoryParameterSpec getInventoryParameter() {
    InventoryParameterSpec inventoryParameterSpec = new InventoryParameterSpec();
    inventoryParameterSpec.setProtocolID(new AirProtocols(AirProtocols.EPCGlobalClass1Gen2));
    inventoryParameterSpec.setInventoryParameterSpecID(new UnsignedShort(1));
    return inventoryParameterSpec;
  }

  private ROBoundarySpec getBoundarySpec() {
    ROBoundarySpec boundarySpec = new ROBoundarySpec();
    boundarySpec.setROSpecStartTrigger(getStartTrigger());
    boundarySpec.setROSpecStopTrigger(getStopTrigger());
    return boundarySpec;
  }

  private ROSpecStopTrigger getStopTrigger() {
    ROSpecStopTrigger stopTrigger = new ROSpecStopTrigger();
    stopTrigger.setDurationTriggerValue(new UnsignedInteger(0));
    stopTrigger.setROSpecStopTriggerType(new ROSpecStopTriggerType(ROSpecStopTriggerType.Null));
    return stopTrigger;
  }

  private ROSpecStartTrigger getStartTrigger() {
    ROSpecStartTrigger startTrigger = new ROSpecStartTrigger();
    startTrigger.setROSpecStartTriggerType(new ROSpecStartTriggerType(ROSpecStartTriggerType.Null));
    return startTrigger;
  }

  private void startSpec() throws TimeoutException {
    START_ROSPEC message = new START_ROSPEC();
    message.setROSpecID(rospecId);
    transact(message);
  }

  private void stopSpec() throws TimeoutException {
    STOP_ROSPEC message = new STOP_ROSPEC();
    message.setROSpecID(rospecId);
    transact(message);
  }

  private AntennaConfiguration configuracaoDaAntena(AntennaConfig antena) {
    AntennaConfiguration config = new AntennaConfiguration();
    config.setAntennaID(new UnsignedShort(antena.getNumber()));
    RFReceiver receiver = new RFReceiver();
    receiver.setReceiverSensitivity(sensibilidadeLLRP(100));
    config.setRFReceiver(receiver);
    RFTransmitter transmitter = new RFTransmitter();
    transmitter.setTransmitPower(potenciaLLRP(antena.getTransmitPower()));
    transmitter.setChannelIndex(new UnsignedShort(0));
    transmitter.setHopTableID(new UnsignedShort(0));
    config.setRFTransmitter(transmitter);
    return config;
  }

  private UnsignedShort sensibilidadeLLRP(int sensibilidade) {
    return new UnsignedShort((128 * sensibilidade) / 100);
  }

  private UnsignedShort potenciaLLRP(int potencia) {
    return new UnsignedShort((255 * potencia) / 100);
  }

  private void configReader() throws TimeoutException {
    SET_READER_CONFIG config = new SET_READER_CONFIG();
    config.setResetToFactoryDefault(YES);
    List<AntennaConfiguration> antennasConfig = config.getAntennaConfigurationList();
    leitor().getActiviesAntennas().forEach(
      antena -> antennasConfig.add(configuracaoDaAntena(antena)));
    transact(config);
  }

  @Override
  public void startReader() {
    try {
      connect();
      deleteROSpec();
      configReader();
      addROSpec();
      enableROSpec();
      startSpec();
    } catch (TimeoutException ex) {
      throw new ReaderException("Tempo de conexão com o leitor excedido", ex);
    }
  }

  @Override
  public void stopReader() {
    try {
      stopSpec();
      deleteROSpec();
      disconect();
    } catch (TimeoutException ex) {
      LOGGER.error(ex.getMessage(), ex);
    }
  }

  @Override
  public void errorOccured(String errorMessage) {
    LOGGER.error(errorMessage);
  }

  @Override
  public void messageReceived(LLRPMessage message) {
    if (message.getTypeNum() == RO_ACCESS_REPORT.TYPENUM) {
      RO_ACCESS_REPORT report = (RO_ACCESS_REPORT) message;
      List<TagReportData> reportDatas = report.getTagReportDataList();
      for (TagReportData tagReportData : reportDatas) {
        EPC_96 epc96 = (EPC_96) tagReportData.getEPCParameter();
        int antena = 1;
        if (tagReportData.getAntennaID() != null) {
          antena = tagReportData.getAntennaID().getAntennaID().toInteger();
        }
        onRead(new Tag(epc96.getEPC().toString()), antena);
      }
    }
  }

  @Override
  public String toString() {
    return "Leitor LLRP: " + leitor().getHostName();
  }

}
