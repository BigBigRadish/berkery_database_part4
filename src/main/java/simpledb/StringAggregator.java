package simpledb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Knows how to compute some aggregate over a set of StringFields.
 */
public class StringAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;

    //������ֵָ����Ҫʹ��tuple����һ����������
    int gbIndex;

    //������ָ����Ҫʹ��tuple����һ�������ۺ�
    int agIndex;

    //�ۺ�ǰtuple��������
    TupleDesc originalTd;

    //ָ������Ϊ�������ݵ���һ�е�ֵ������
    Type gbFieldType;

    //ָ��ʹ�����־ۺϲ���
    Op aggreOp;

    //group-by value��aggregate value��ӳ��
    HashMap<Field, Integer> gval2agval;

    //�ۺϺ��td
    private TupleDesc td;

    /**
     * Aggregate constructor
     *
     * @param gbIndex     the 0-based index of the group-by field in the tuple, or NO_GROUPING if there is no grouping
     * @param gbfieldtype the type of the group by field (e.g., Type.INT_TYPE), or null if there is no grouping
     * @param agIndex     the 0-based index of the aggregate field in the tuple
     * @param aggreOp     aggregation operator to use -- only supports COUNT
     * @param td          �Ҽ��ϵ�һ���������ɾۺ�����ʹ����(һ����Aggregate��)������
     * @throws IllegalArgumentException if aggreOp != COUNT
     */

    public StringAggregator(int gbIndex, Type gbfieldtype, int agIndex, Op aggreOp,TupleDesc td) {
        // some code goes here
        if (aggreOp != Op.COUNT) {
            throw new UnsupportedOperationException("String����ֵֻ֧��count����,��֧��" + aggreOp);
        }
        this.gbIndex = gbIndex;
        this.agIndex = agIndex;
        this.aggreOp = aggreOp;
        this.td = td;
        this.gbFieldType = gbfieldtype;
        gval2agval = new HashMap<>();
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the constructor
     *
     * @param tup the Tuple containing an aggregate field and a group-by field
     * @throws IllegalArgumentException �����tuple��ָ���в���Type.STRING_TYPE���ͻ���ۺ�tuple��tupleDesc��֮ǰ��һ��
     */
    public void mergeTupleIntoGroup(Tuple tup) throws IllegalArgumentException {
        // some code goes here
        //���ۺ�ֵ���ڵ�Field
        Field aggreField;
        //�������ݵ�Field
        Field gbField = null;
        //�µľۺϽ��
        Integer newVal;
        aggreField = tup.getField(agIndex);

        if (aggreField.getType() != Type.STRING_TYPE) {
            throw new IllegalArgumentException("��tuple��ָ���в���Type.STRING_TYPE����");
        }
        //��ʼ��originalTd����ȷ��ÿһ�ξۺϵ�tuple��td������ͬ
        if (originalTd == null) {
            originalTd = tup.getTupleDesc();
        } else if (!originalTd.equals(tup.getTupleDesc())) {
            throw new IllegalArgumentException("���ۺ�tuple��tupleDesc��֮ǰ��һ��");
        }
        if (gbIndex != Aggregator.NO_GROUPING) {
            //���gbIdexΪNO_GROUPING����ô���ø�gbField��ֵ����Ϊ��ʼֵnull����
            gbField = tup.getField(gbIndex);
        }

        //��ʼ���оۺϲ���
        if (gval2agval.containsKey(gbField)) {
            Integer oldVal = gval2agval.get(gbField);
            newVal = oldVal + 1;
        } else newVal = 1;
        gval2agval.put(gbField, newVal);
    }

    /**
     * Create a DbIterator over group aggregate results.
     *
     * @return a DbIterator whose tuples are the pair (groupVal,
     * aggregateVal) if using group, or a single (aggregateVal) if no
     * grouping. The aggregateVal is determined by the type of
     * aggregate specified in the constructor.
     */
    public DbIterator iterator() {
        // some code goes here
        ArrayList<Tuple> tuples = new ArrayList<>();
        for (Map.Entry<Field, Integer> g2a : gval2agval.entrySet()) {
            Tuple t = new Tuple(td);//��tuple����setRecordId����ΪRecordId�Խ��в������tupleû������
            //�ֱ����������з��������
            if (gbIndex == Aggregator.NO_GROUPING) {
                t.setField(0, new IntField(g2a.getValue()));
            } else {
                t.setField(0, g2a.getKey());
                t.setField(1, new IntField(g2a.getValue()));
            }
            tuples.add(t);
        }
        return new TupleIterator(td, tuples);
    }

}