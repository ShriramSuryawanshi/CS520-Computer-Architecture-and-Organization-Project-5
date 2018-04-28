/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import baseclasses.PipelineRegister;
import baseclasses.PipelineStageBase;
import baseclasses.CpuCore;
import baseclasses.InstructionBase;
import tools.InstructionSequence;
import utilitytypes.IGlobals;
import utilitytypes.IPipeReg;
import utilitytypes.IPipeStage;
import static utilitytypes.IProperties.*;
import utilitytypes.IRegFile;
import utilitytypes.Logger;
import voidtypes.VoidRegister;

/**
 * This is an example of a class that builds a specific CPU simulator out of
 * pipeline stages and pipeline registers.
 *
 * @author
 */
public class MyCpuCore extends CpuCore {

    static final String[] producer_props = {RESULT_VALUE};

    public void initProperties() {
        properties = new GlobalData();

        // @shree - initializing rat
        IGlobals globals = (GlobalData) getCore().getGlobals();
        IRegFile regfile = globals.getRegisterFile();

        for (int i = 0; i < 32; i++) {
            GlobalData.rat[i] = i;
            regfile.markUsed(i, true);
        }

        for (int i = 0; i < 256; i++) {
            GlobalData.IQ[i] = "NULL";
        }
    }

    public void loadProgram(InstructionSequence program) {
        getGlobals().loadProgram(program);
    }

    public void runProgram() {
        properties.setProperty("running", true);
        while (properties.getPropertyBoolean("running")) {
            Logger.out.println("## Cycle number: " + cycle_number);

            IGlobals globals = (GlobalData) getCore().getGlobals();
            IRegFile regfile = globals.getRegisterFile();

            //@shree - setting registers free
            String RegNames = "";
            for (int i = 0; i < 256; i++) {

                if ((!regfile.isInvalid(i)) && (regfile.isRenamed(i)) && (regfile.isUsed(i))) {
                    regfile.markUsed(i, false);

                    RegNames = RegNames + " P" + i;
                }
            }

            if (RegNames.length() > 0) {
                Logger.out.println("# Freeing:" + RegNames);
            }

            advanceClock();
        }
    }

    @Override
    public void createPipelineRegisters() {
        createPipeReg("FetchToDecode");
        createPipeReg("DecodeToIQ");
        createPipeReg("IQToExecute");
        createPipeReg("IQToMemory");
        createPipeReg("IQToIntMul");
        createPipeReg("IQToFloatAddSub");
        createPipeReg("IQToFloatDiv");
        createPipeReg("IQToFloatMul");
        createPipeReg("IQToIntDiv");
        createPipeReg("ExecuteToWriteback");
        createPipeReg("FDivToWriteback");
        createPipeReg("IDivToWriteback");
        // createPipeReg("MemoryToWriteback");
    }

    @Override
    public void createPipelineStages() {
        addPipeStage(new AllMyStages.Fetch(this));
        addPipeStage(new AllMyStages.Decode(this));
        addPipeStage(new AllMyStages.Execute(this));
        addPipeStage(new FloatDiv(this));
        addPipeStage(new IntDiv(this));
        addPipeStage(new IssueQueue(this));

        //  addPipeStage(new AllMyStages.Memory(this));
        addPipeStage(new AllMyStages.Writeback(this));
    }

    @Override
    public void createChildModules() {
        // MSFU is an example multistage functional unit.  Use this as a
        // basis for FMul, IMul, and FAddSub functional units.

        addChildUnit(new IntMul(this, "IntMul"));
        addChildUnit(new FloatAddSub(this, "FloatAddSub"));
        addChildUnit(new FloatMul(this, "FloatMul"));
        addChildUnit(new MemUnit(this, "MemUnit"));

    }

    @Override
    public void createConnections() {
        // Connect pipeline elements by name.  Notice that 
        // Decode has multiple outputs, able to send to Memory, Execute,
        // or any other compute stages or functional units.
        // Writeback also has multiple inputs, able to receive from 
        // any of the compute units.
        // NOTE: Memory no longer connects to Execute.  It is now a fully 
        // independent functional unit, parallel to Execute.

        // Connect two stages through a pipelin register
        connect("Fetch", "FetchToDecode", "Decode");
        connect("Decode", "DecodeToIQ", "IssueQueue");
        connect("IssueQueue", "IQToExecute", "Execute");
        connect("IssueQueue", "IQToMemory", "MemUnit");
        connect("IssueQueue", "IQToIntDiv", "IntDiv");
        connect("IssueQueue", "IQToFloatDiv", "FloatDiv");
        connect("IssueQueue", "IQToIntMul", "IntMul");
        connect("IssueQueue", "IQToFloatAddSub", "FloatAddSub");
        connect("IssueQueue", "IQToFloatMul", "FloatMul");

        // Writeback has multiple input connections from different execute
        // units.  The output from MSFU is really called "MSFU.Delay.out",
        // which was aliased to "MSFU.out" so that it would be automatically
        // identified as an output from MSFU.
        connect("Execute", "ExecuteToWriteback", "Writeback");
        connect("IntDiv", "IDivToWriteback", "Writeback");
        connect("FloatDiv", "FDivToWriteback", "Writeback");
        connect("FloatAddSub", "Writeback");
        connect("FloatMul", "Writeback");
        connect("IntMul", "Writeback");
        connect("MemUnit", "Writeback");
    }

    @Override
    public void specifyForwardingSources() {
        addForwardingSource("ExecuteToWriteback");
        addForwardingSource("IDivToWriteback");
        addForwardingSource("FDivToWriteback");

        //   addForwardingSource("MemoryToWriteback");
        // MSFU.specifyForwardingSources is where this forwarding source is added
        // addForwardingSource("MSFU.out");
    }

    @Override
    public void specifyForwardingTargets() {
        // Not really used for anything yet
    }

    @Override
    public IPipeStage getFirstStage() {
        // CpuCore will sort stages into an optimal ordering.  This provides
        // the starting point.
        return getPipeStage("Fetch");
    }

    public MyCpuCore() {
        super(null, "core");
        initModule();
        printHierarchy();
        Logger.out.println("");
    }
}
