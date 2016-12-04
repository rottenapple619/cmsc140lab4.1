/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

import net.Connections;

/**
 *
 * 
 */
public class FingerTable {

    /**
     * Stores the entries of the FingerTable
     */
    private final NodeRef entries[];
    
    
    /**
     * true if FingerTable is updated
     * otherwise false
     */
    private boolean isUpdated;
    
    
    public FingerTable(){
        this.entries = new NodeRef[Connections.isLocal? HashFunction.LOCAL_KEY:HashFunction.NETWORK_KEY];
        this.isUpdated = false;
    }
    
    
    public void update(Long basePeerID, NodeRef joiningPeer){
        
        int j, KEY = Connections.isLocal? HashFunction.LOCAL_KEY:HashFunction.NETWORK_KEY;
        Long compareID;
        NodeRef peerStored;
        
        for(j=0;j<KEY;j++){
            compareID = (long) ((basePeerID+Math.pow(2, j))%Math.pow(2, KEY));
            peerStored = this.entries[j];
            
            if(compareID == joiningPeer.getID()){
                if(peerStored==null){
                    this.entries[j]=joiningPeer;
                    this.isUpdated = true;
                }
                else if(!(peerStored.equals(joiningPeer))){
                    this.entries[j]=joiningPeer;
                    this.isUpdated = true;
                }
                continue;
            }
            if(peerStored==null){
                if(isSuccessor(compareID,basePeerID,joiningPeer.getID())){
                    this.entries[j]=joiningPeer;
                    this.isUpdated = true;
                }
                else{
                    //do nothing
                }
            }
            else{
                if(isSuccessor(joiningPeer.getID(),compareID,peerStored.getID())){
                    if(!(peerStored.equals(joiningPeer))){
                        this.entries[j]=joiningPeer;
                        this.isUpdated = true;
                    }
                }
                else{
                    //do nothing
                }
            }
            
        }//end for-loop
        
        if(this.isUpdated){
            this.isUpdated = false;
            this.printFingerTable();
        }
            
    }
    
    /**
     * Prints the entries of the FingerTable
     */
    private void printFingerTable(){
        int j, KEY = Connections.isLocal? HashFunction.LOCAL_KEY:HashFunction.NETWORK_KEY;
        Log.print("------------------------------------------------------------");
        Log.print("Finger Table Update:");
        for(j=0; j<KEY;j++){
            if(!(this.entries[j]==null)){
                Log.print("Entry "+j+": "+HashFunction.displayID(this.entries[j].getID()));
            }
            else{
                Log.print("Entry "+j+": null");
            }
        }
        Log.print("------------------------------------------------------------");
    }
    
    
    public NodeRef getNearestPeer(NodeRef basePeer, Long compareID){
        
        int j, KEY = Connections.isLocal? HashFunction.LOCAL_KEY:HashFunction.NETWORK_KEY;
        NodeRef nearestPeer = basePeer;
        NodeRef peerStored = null;
        
        for(j=0;j<KEY;j++){
            peerStored = this.entries[j];
           
            if(peerStored==null)
                break;

            if(isSuccessor(compareID,nearestPeer.getID(),peerStored.getID()))
                break;
            
            nearestPeer = peerStored;
        }
        
        return nearestPeer;
    }
    
    /**
     * tests whether newEntry immediately precedes comparator with respect to successorOfComparator
     * @param newEntry
     * @param comparator
     * @param successorOfComparator the current successor of comparator
     * @return true if newEntry immediately precedes comparator
     */
    public static boolean isSuccessor(Long newEntry, Long comparator, Long successorOfComparator) {//amazing Lyle
        return ((newEntry > comparator && newEntry <= successorOfComparator)
            || (comparator > successorOfComparator && (newEntry > comparator || newEntry < successorOfComparator)));
    }
}
