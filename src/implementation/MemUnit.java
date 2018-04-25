/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import baseclasses.FunctionalUnitBase;
import baseclasses.InstructionBase;
import baseclasses.Latch;
import baseclasses.ModuleBase;
import baseclasses.PipelineStageBase;
import baseclasses.PropertiesContainer;
import java.util.Map;
import tools.MultiStageDelayUnit;
import tools.MyALU;
import utilitytypes.IFunctionalUnit;
import utilitytypes.IGlobals;
import utilitytypes.IModule;
import utilitytypes.IPipeReg;
import utilitytypes.IPipeStage;
import utilitytypes.IProperties;
import static utilitytypes.IProperties.MAIN_MEMORY;
import utilitytypes.Operand;
import voidtypes.VoidProperties;

/**
 *
 * @author millerti
 */
public class MemUnit extends FunctionalUnitBase {

    public MemUnit(IModule parent, String name) {
        super(parent, "MemUnit");
    }

    private static class Addr extends PipelineStageBase {

        public Addr(IModule parent) {
            super(parent, "in:Addr");
        }

        @Override
        public void compute(Latch input, Latch output) {
            if (input.isNull()) {
                return;
            }

            doPostedForwarding(input);
            InstructionBase ins = input.getInstruction();

            int source1 = ins.getSrc1().getValue();
            int source2 = ins.getSrc2().getValue();

            int addr = source1 + source2;

            output.setProperty("address", addr);
            output.setInstruction(ins);
        }
    }

    private static class LSQ extends PipelineStageBase {

        public LSQ(IModule parent) {
            super(parent, "LSQ");
        }

        @Override
        public void compute(Latch input, Latch output) {
            if (input.isNull()) {
                return;
            }

            this.addStatusWord("Addr=" + input.getPropertyInteger("address"));

            output.copyAllPropertiesFrom(input);
            output.setInstruction(input.getInstruction());
        }
    }

    private static class DCache extends PipelineStageBase {

        public DCache(IModule parent) {
            // For simplicity, we just call this stage "in".
            super(parent, "DCache");
//            super(parent, "in:Math");  // this would be fine too
        }

        @Override
        public void compute(Latch input, Latch output) {
            if (input.isNull()) {
                return;
            }

            InstructionBase ins = input.getInstruction();

            Operand oper0 = ins.getOper0();
            int oper0val = ins.getOper0().getValue();
            int addr = input.getPropertyInteger("address");

            int value = 0;
            IGlobals globals = (GlobalData) getCore().getGlobals();
            int[] memory = globals.getPropertyIntArray(MAIN_MEMORY);

            switch (ins.getOpcode()) {
                case LOAD:
                    // Fetch the value from main memory at the address
                    // retrieved above.
                    value = memory[addr];
                    output.setResultValue(value);
                    output.setInstruction(ins);
                    addStatusWord(oper0.getRegisterName() + "=Mem[" + addr + "]");
                    break;

                case STORE:
                    // For store, the value to be stored in main memory is
                    // in oper0, which was fetched in Decode.
                    memory[addr] = oper0val;
                    addStatusWord("Mem[" + addr + "]=" + oper0.getValueAsString());
                    return;

                default:
                    throw new RuntimeException("Non-memory instruction got into Memory stage");
            }
        }
    }

    @Override
    public void createPipelineRegisters() {
        createPipeReg("AddrToLSQ");
        createPipeReg("LSQToDCache");
        createPipeReg("out");
    }

    @Override
    public void createPipelineStages() {
        addPipeStage(new Addr(this));
        addPipeStage(new LSQ(this));
        addPipeStage(new DCache(this));
    }

    @Override
    public void createChildModules() {
    }

    @Override
    public void createConnections() {
        //addRegAlias("Delay.out", "out");
        connect("in:Addr", "AddrToLSQ", "LSQ");
        connect("LSQ", "LSQToDCache", "DCache");
        connect("DCache", "out");
    }

    @Override
    public void specifyForwardingSources() {
        addForwardingSource("out");
    }
}
