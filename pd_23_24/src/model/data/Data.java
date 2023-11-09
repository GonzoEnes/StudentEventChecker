package model.data;

import resources.ResourceManager;

import java.sql.SQLException;
import java.util.ArrayList;

public class Data {

    private ResourceManager resourceManager;

    public Data(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public boolean connectToDB(String DBDirectory, int port){
        return this.resourceManager.connectToDB(DBDirectory, port);
    }

    /*public int getVersion() {
        this.resourceManager.getVersion();
    }*/

    public boolean insertUser(ArrayList<String> parameters){
        return this.resourceManager.insertUser(parameters);
    }

    public int insertEvent(ArrayList<String> params) { return this.resourceManager.insertEvent(params); }

    public int verifyLogin(ArrayList<String> params){ return this.resourceManager.verifyLogin(params); }

    public boolean editProfile(ArrayList<String> params, String email){ return this.resourceManager.editProfile(params, email); }

    public String listPresencas(Integer idEvento, Integer idClient) {
        return this.resourceManager.listPresencas(idEvento, idClient);
    }
}
