package supervisor;

import view.Principal;
import connection.TelnetConnection;
import controller.Console;
import controller.Info;
import controller.TableInfo;

public class Supervisor4Slave extends Supervisor4Master{
	
	private TelnetConnection conexaoColetorSlave;

	private int idMaster;
	private int idSlave;

	public int getIdMaster() {
		return idMaster;
	}

	public void setIdMaster(int idMaster) {
		this.idMaster = idMaster;
	}

	public int getIdSlave() {
		return idSlave;
	}

	public void setIdSlave(int idSlave) {
		this.idSlave = idSlave;
	}
	
	public TelnetConnection getConexaoColetorSlave() {
		return conexaoColetorSlave;
	}

	public void setConexaoColetorSlave(TelnetConnection conexaoColetorSlave) {
		this.conexaoColetorSlave = conexaoColetorSlave;
	}
	
	@Override
	public boolean update(){
		
		String SN  = getSerialNumber();
		int row = TableInfo.getRow(SN);
		boolean flag = Principal.getTabela().getValueAt(row, 5).equals("ATUALIZAR[X]");
		
		if(row!=-1 && flag){
			TelnetConnection conexao = getConexaoColetorSlave();
			int tentativas=0;
			
			while(true){
				
				if(conexao.sendCommand("ping -c 1 "+"169.254."+(Integer.parseInt(getId())+127)+".1").contains(" 1 packets received")){
					break;
				}else{
					Console.print("Tentando conexao com "+SN);
					conexao.sendCommand("sleep 20");
					tentativas++;
					if(tentativas>=6){
						Console.print("sem conexao com "+SN);
						return false;
					}
				}
			}
			
			conexao.connectVlan101("169.254."+(Integer.parseInt(getId())+127)+".1");
			conexao.connectVlan102("169.254."+getIdSlave()+".37");
			stopSupervisor(conexao);
			Console.print("Iniciando atualiza��o em : " + getSerialNumber());
			nameScript = Info.getFileUpgrade().getName();
			conexao.sendCommand("chmod +x " + nameScript);
			conexao.sendCommand("touch update.log");
			conexao.sendCommand("./" + nameScript + " >./update.log &");
			TableInfo.refresh(getSerialNumber(), 4, "Em atualiza��o");
			Console.print("Aguarde 60 segundos.");
			sleep(conexao, 60);
			
			while (true) {
				status = conexao.sendCommand("cat update.log");
				flag = status.contains(msgEndUpgrade);

				if (flag) {
					Console.print("Fim da atualiza��o...");
					if (status.contains(msnNoSpace)) {
						Console.print("Atualiza��o falhou devido a falta de espa�o.");
						TableInfo.refresh(getSerialNumber(), 4, "Falta de espa�o.");
						conexao.disconnect();
						conexao.disconnect();
						return false;
					}
				Console.print("Aguarde 4 segundos.");	
				sleep(conexao, 4);

					while (true) {
						status = conexao.sendCommand("cat update.log");
						flag = status.contains(msgSyslogChange);
						if (flag) {
							Console.print("Aguarde 4 segundos.");
							sleep(conexao, 4);
							conexao.sendCommand("killall klogd");
							Console.print("Atualizando tabela");
							refreshTable(conexao,8887);
							runDefaultConfig(conexao);//defaultConfig
							conexao.sendCommand("reboot");
							TableInfo.refresh(getSerialNumber(), 4, "Unidade reinicializada");
							TableInfo.refresh(getSerialNumber(), 5, "-");
							//rebootNotColetor(conexao);
							conexao.disconnect();
							conexao.disconnect();
							return true;
						} else if (status.contains(msgSyslognNoChange)) {
							Console.print("Atualizando tabela");
							refreshTable(conexao,8887);
							runDefaultConfig(conexao);//defaultConfig
							conexao.sendCommand("reboot");
							TableInfo.refresh(getSerialNumber(), 4, "Unidade reinicializada");
							TableInfo.refresh(getSerialNumber(), 5, "-");
							//rebootNotColetor(conexao);
							conexao.disconnect();
							conexao.disconnect();
							return true;
						} else {
							Console.print("Aguarde 4 segundos.");
							sleep(conexao, 4);
						}
					}
				} else {
					Console.print("Aguarde 10 segundos.");
					sleep(conexao, 10);
				}
			}
			
		}else{
			return false;
		}
		
	}
	
	public void runDefaultConfig(TelnetConnection conexao){
		String contemDefaultConfig = conexao.sendCommand("ls");
		
		if(contemDefaultConfig.contains("default_config.sh")){
			Console.print("Executando default config no supervidor slave " +getIdSlave()+".");
			conexao.sendCommand("./default_config.sh", "(s/n) ");
			conexao.sendCommand("n");
		}
		
	}
}
