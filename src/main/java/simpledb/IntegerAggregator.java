package simpledb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Knows how to compute some aggregate over a set of IntFields.
 *
 * �Ҽ��˲�������������Ŀ�������þۺ�����ʹ����(������aggregate��������Aggregate��)��
 * �������aggregator�ۺϺ������������Ϊ�ۺ��������ɽ���ĵ�����ʱ��Ҫʹ�õ�td��Ȼ���ⲿ���߼���Aggregate����Ҳ��Ҫ
 * �Ѿ���Aggregate��ʵ���ˣ�û��Ҫ�ظ���������Aggregate����������ۺ�������ۺϺ��������
 * ��ô��Ϊʲô���ھۺ�����ʵ�֡��õ��ۺϺ������������Ȼ����ʹ���ߵ��þͺ����أ�
 * ������Ϊ��ԭ����У�Aggregate�Ĳ�������һ�����������½���Aggregate�࣬��û�н��оۺϵ�ǰ���¾͵�����getTupleDesc
 * ��������ǵ��þۺ����ķ��������ھۺ�����û�������κ�һ��tuple�������޷�ȷ���ۺϺ�����������ͻ᷵��null���߳���
 */

public class IntegerAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;

    //������ֵָ����Ҫʹ��tuple����һ����������
    int gbIndex;

    //������ָ����Ҫʹ��tuple����һ�������ۺ�
    int agIndex;

    //�ۺ�ǰtuple��������
    TupleDesc originalTd;

    //�ۺϺ��tuple��������
    TupleDesc td;

    //ָ������Ϊ�������ݵ���һ�е�ֵ������
    Type gbFieldType;

    //ָ��ʹ�����־ۺϲ���
    Op aggreOp;

    //Key��ÿ����ͬ�ķ����ֶ�(groupby value)  Vlaue���ۺϵĽ��
    HashMap<Field, Integer> gval2agval;

    //Key��ÿ����ͬ�ķ����ֶ�(groupby value)  Value���÷������ƽ��ֵ�ۺϹ��̴��������ֵ�ĸ����Լ����ǵĺ�
    //���map�����ڸ����ڼ���ƽ��ֵʱ�õ���ǰ�ۺϹ�������
    HashMap<Field, Integer[]> gval2count_sum;

    /**
     * Aggregate constructor
     *
     * @param gbIndex     the 0-based index of the group-by field in the tuple, or
     *                    NO_GROUPING if there is no grouping
     * @param gbFieldType the type of the group by field (e.g., Type.INT_TYPE), or null
     *                    if there is no grouping
     * @param agIndex     the 0-based index of the aggregate field in the tuple
     * @param aggreOp     the aggregation operator
     * @param td          �Ҽ��ϵ�һ���������ɾۺ�����ʹ����(һ����Aggregate��)������
     */

    public IntegerAggregator(int gbIndex, Type gbFieldType, int agIndex, Op aggreOp,TupleDesc td) {
        // some code goes here
        this.gbIndex = gbIndex;
        this.gbFieldType = gbFieldType;
        this.agIndex = agIndex;
        this.aggreOp = aggreOp;
        this.td=td;
        gval2agval = new HashMap<>();
        gval2count_sum = new HashMap<>();
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the
     * constructor
     *
     * @param tup the Tuple containing an aggregate field and a group-by field
     * @throws IllegalArgumentException �����tuple��ָ���в���TYPE.INT_TYPE���ͻ���ۺ�tuple��tupleDesc��֮ǰ��һ��
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
        //���ۺ�ֵ
        int toAggregate;
        if (aggreField.getType() != Type.INT_TYPE) {
            throw new IllegalArgumentException("��tuple��ָ���в���Type.INT_TYPE����");
        }
        toAggregate = ((IntField) aggreField).getValue();
        //��ʼ��originalTd����ȷ��ÿһ�ξۺϵ�tuple��td������ͬ
        if (originalTd == null) {
            originalTd = tup.getTupleDesc();
        } else if (!originalTd.equals(tup.getTupleDesc())) {
            throw new IllegalArgumentException("���ۺ�tuple��tupleDesc��һ��");
        }
        if (gbIndex != Aggregator.NO_GROUPING) {
            //���gbIdexΪNO_GROUPING����ô���ø�gbField��ֵ����Ϊ��ʼֵnull����
            gbField = tup.getField(gbIndex);
        }
        //��ʼ���оۺϲ���
        //ƽ��ֵ�Ĳ�����Ҫά��gval2count_sum�����Ե�������
        if (aggreOp == Op.AVG) {
            if (gval2count_sum.containsKey(gbField)) {//������map�Ѿ�������������
                Integer[] oldCountAndSum = gval2count_sum.get(gbField);//֮ǰ����÷�����ܴ����Լ����в������ĺ�
                int oldCount = oldCountAndSum[0];
                int oldSum = oldCountAndSum[1];
                //���¸÷����Ӧ�ļ�¼����������1,�����ܺͼ��ϴ��ۺϵ�ֵ
                gval2count_sum.put(gbField, new Integer[]{oldCount + 1, oldSum + toAggregate});
            } else {//����Ϊ��һ�δ���÷����tuple
                gval2count_sum.put(gbField, new Integer[]{1, toAggregate});
            }
            //ֱ����gval2count_sum���map��¼����Ϣ�õ��÷����Ӧ�ľۺ�ֵ��������gval2agval��
            Integer[] c2s=gval2count_sum.get(gbField);
            int currentCount = c2s[0];
            int currentSum = c2s[1];
            gval2agval.put(gbField, currentSum / currentCount);
            //������������˷���ʣ�µĴ����Ƕ�Ӧ������ƽ��ֵ�����Ĳ�����
            return;
        }

        //������ƽ��ֵ�������ۺϲ���
        if (gval2agval.containsKey(gbField)) {
            Integer oldVal = gval2agval.get(gbField);
            newVal = calcuNewValue(oldVal, toAggregate, aggreOp);
        } else if (aggreOp == Op.COUNT) {//����Ƕ�Ӧ����ĵ�һ���μӾۺϲ�����tuple����ô����count��������������������Ǵ��ۺ�ֵ
            newVal = 1;
        } else {
            newVal = toAggregate;
        }
        gval2agval.put(gbField, newVal);
    }

    /**
     * �ɾɵľۺϽ�����µľۺ�ֵ�õ��µľۺϽ��
     *
     * @param oldVal      �ɵľۺϽ��
     * @param toAggregate �µľۺ�ֵ
     * @param aggreOp     �ۺϲ���
     * @return �µľۺ�ֵ
     */
    private int calcuNewValue(int oldVal, int toAggregate, Op aggreOp) {
        switch (aggreOp) {
            case COUNT:
                return oldVal + 1;
            case MAX:
                return Math.max(oldVal, toAggregate);
            case MIN:
                return Math.min(oldVal, toAggregate);
            case SUM:
                return oldVal + toAggregate;
            default:
                throw new IllegalArgumentException("��Ӧ�õ�������");
        }
    }

    /**
     * Create a DbIterator over group aggregate results.
     *
     * @return a DbIterator whose tuples are the pair (groupVal, aggregateVal)
     * if using group, or a single (aggregateVal) if no grouping. The
     * aggregateVal is determined by the type of aggregate specified in
     * the constructor.
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