import java.util.Iterator;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

public class Sac_Dos {
 private double [] p;
 private double [] w;
 private double W;
 int n;
 
 IloCplex model;
 IloNumVar [] x;
 
	public Sac_Dos(double[] p, double[] w, double w2) throws IloException {
	this.p = p;
	this.w = w;
	W = w2;
	n=p.length;
	model=new IloCplex();
	creationModel();
	System.out.println(model.toString());
}

	public void creationModel() throws IloException {
		// TODO Auto-generated method stub
		creationVariables();
		creationConstarints();
		creationObjective();
	}
	
	public void creationObjective() throws IloException {
		// TODO Auto-generated method stub
		IloLinearNumExpr fo=model.linearNumExpr();
			for (int i = 0; i < n; i++) {
				fo.addTerm(p[i], x[i]);
			}
		model.addMaximize(fo);
	}

	public void creationConstarints() throws IloException {
		// TODO Auto-generated method stub
		IloLinearNumExpr lin=model.linearNumExpr();
		for (int i = 0; i <n; i++) {
			lin.addTerm(x[i], w[i]);
		}
		model.addLe(lin, W);
	}

	public void creationVariables() throws IloException {
		x=model.boolVarArray(n);
	}

    public boolean solve() throws IloException {
    	return model.solve();
    }
    
    public double [] getX() throws IloException {
    	double [] d=new double[n];
    	if(solve()) {
    		d=model.getValues(x);
    	}
    		
    	return d;
    }
    
    public void getSolutionSac_Dos() throws IloException {
    	double [] d=getX();
    	for (int i = 0; i < d.length; i++) {
			if(d[i]==1)
				System.out.println("objet "+(i+1));
		}
    }
    
    







	public static void main(String[] args) throws IloException {
		// TODO Auto-generated method stub
        double [] p= {4,2,1,10,2,2};
        double [] w= {12,2,1,4,1,3};
        Sac_Dos s1=new Sac_Dos(p, w, 15);
            s1.getSolutionSac_Dos();
	}

}
