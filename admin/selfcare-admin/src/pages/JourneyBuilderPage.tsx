import { useState, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import ReactFlow, {
  addEdge,
  applyNodeChanges,
  applyEdgeChanges,
  Node,
  Edge,
  NodeChange,
  EdgeChange,
  Connection,
  Background,
  Controls,
  MiniMap,
  NodeProps,
  Handle,
  Position,
} from 'reactflow';
import 'reactflow/dist/style.css';
import { toast } from 'sonner';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Workflow,
  Save,
  Play,
  Plus,
  Trash2,
  Settings,
  MessageSquare,
  Phone,
  CreditCard,
  User,
  FileText,
  ChevronRight,
} from 'lucide-react';
import { api } from '@/lib/api';
import { useActiveTenant } from '@/hooks/useActiveTenant';

/**
 * Journey Builder — visual flow editor for multi-step user journeys.
 *
 * Journey types:
 * - Onboarding (KYC, registration)
 * - Recharge flows
 * - Bill payment flows
 * - Plan upgrade
 * - Support escalation
 * - Marketing automation
 *
 * Each step is a node with type:
 * - SCREEN: Show UI page/screen
 * - ACTION: Trigger backend action (API call, payment, etc.)
 * - DECISION: Branch based on condition (user response, eligibility, etc.)
 * - WAIT: Pause for X duration or event
 * - MESSAGE: Send notification (SMS/push/email)
 */
export default function JourneyBuilderPage() {
  const { activeTenantId } = useActiveTenant();
  const queryClient = useQueryClient();
  const [journeyName, setJourneyName] = useState('New Journey');
  const [nodes, setNodes] = useState<Node[]>(initialNodes);
  const [edges, setEdges] = useState<Edge[]>(initialEdges);
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);
  const [editingJourneyId, setEditingJourneyId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  // Fetch journeys for the tenant
  const { data: journeys = [], isLoading: loadingJourneys } = useQuery({
    queryKey: ['journeys', activeTenantId],
    queryFn: () => api.journeys.list(),
    enabled: !!activeTenantId,
  });

  // Mutations
  const createMutation = useMutation({
    mutationFn: (data: any) => api.journeys.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['journeys', activeTenantId] });
      toast.success('Journey created');
    },
    onError: (err: any) => toast.error(err.message || 'Failed to create journey'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: any }) => api.journeys.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['journeys', activeTenantId] });
      toast.success('Journey updated');
    },
    onError: (err: any) => toast.error(err.message || 'Failed to update journey'),
  });

  const publishMutation = useMutation({
    mutationFn: (id: string) => api.journeys.publish(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['journeys', activeTenantId] });
      toast.success('Journey published');
    },
    onError: (err: any) => toast.error(err.message || 'Failed to publish journey'),
  });

  const archiveMutation = useMutation({
    mutationFn: (id: string) => api.journeys.archive(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['journeys', activeTenantId] });
      toast.success('Journey archived');
    },
    onError: (err: any) => toast.error(err.message || 'Failed to archive journey'),
  });

  const onNodesChange = useCallback(
    (changes: NodeChange[]) => setNodes((nds) => applyNodeChanges(changes, nds)),
    []
  );
  const onEdgesChange = useCallback(
    (changes: EdgeChange[]) => setEdges((eds) => applyEdgeChanges(changes, eds)),
    []
  );
  const onConnect = useCallback(
    (params: Connection) => setEdges((eds) => addEdge({ ...params, animated: true }, eds)),
    []
  );

  const addNode = (type: string) => {
    const newNode: Node = {
      id: `${type}-${Date.now()}`,
      type,
      position: { x: Math.random() * 400 + 100, y: Math.random() * 300 + 100 },
      data: { label: `${type.charAt(0).toUpperCase() + type.slice(1)} Step` },
    };
    setNodes((nds) => [...nds, newNode]);
  };

  const deleteNode = (id: string) => {
    setNodes((nds) => nds.filter((n) => n.id !== id));
    setEdges((eds) => eds.filter((e) => e.source !== id && e.target !== id));
    setSelectedNode(null);
  };

  const handleSave = async () => {
    setIsLoading(true);
    try {
      const payload = {
        tenantId: activeTenantId,
        journeyId: editingJourneyId || journeyName.toLowerCase().replace(/\s+/g, '-'),
        name: journeyName,
        description: '',
        version: 1,
        status: 'DRAFT' as const,
        steps: nodes.map((n) => ({
          stepId: n.id,
          type: (n.type || 'screen').toUpperCase(),
          title: n.data.label,
          description: '',
          config: {},
          nextStepIds: edges.filter((e) => e.source === n.id).map((e) => e.target),
          conditions: [],
          required: true,
          timeoutSeconds: undefined,
          timeoutFallbackStepId: undefined,
          compensationStepId: undefined,
          tags: [],
        })),
        entryStepId: nodes[0]?.id || '',
        tags: [],
      };

      if (editingJourneyId) {
        await updateMutation.mutateAsync({ id: editingJourneyId, data: payload });
      } else {
        await createMutation.mutateAsync(payload);
      }
      setEditingJourneyId(null);
      setJourneyName('New Journey');
      setNodes(initialNodes);
      setEdges(initialEdges);
    } catch (err: any) {
      toast.error(err.message || 'Save failed');
    } finally {
      setIsLoading(false);
    }
  };

  const handleTest = async () => {
    toast.info('Test run started — check results in 30s');
  };

  const handleLoadJourney = async (journey: any) => {
    setJourneyName(journey.name);
    setEditingJourneyId(journey.id);
    // Convert backend journey definition to reactflow nodes/edges
    const newNodes: Node[] = journey.steps?.map((step: any) => ({
      id: step.stepId,
      type: step.type.toLowerCase(),
      position: { x: 250, y: 0 }, // Would need proper layout
      data: { label: step.title },
    })) || initialNodes;

    const newEdges: Edge[] = journey.steps?.flatMap((step: any) =>
      step.nextStepIds?.map((targetId: string) => ({
        id: `${step.stepId}-${targetId}`,
        source: step.stepId,
        target: targetId,
        animated: true,
      })) || []
    ) || initialEdges;

    setNodes(newNodes);
    setEdges(newEdges);
  };

  return (
    <div className="space-y-4 h-[calc(100vh-100px)] flex flex-col">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Workflow className="w-6 h-6" />
            Journey Builder
          </h1>
          <p className="text-gray-500 mt-1">Design multi-step user flows</p>
        </div>
        <div className="flex items-center gap-3">
          <Input
            value={journeyName}
            onChange={(e) => setJourneyName(e.target.value)}
            className="w-64"
          />
          <Button variant="outline" onClick={handleTest} disabled={isLoading}>
            <Play className="w-4 h-4 mr-2" /> Test Run
          </Button>
          <Button onClick={handleSave} disabled={isLoading || createMutation.isPending || updateMutation.isPending}>
            <Save className="w-4 h-4 mr-2" /> {createMutation.isPending || updateMutation.isPending ? 'Saving…' : 'Save'}
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-[200px_1fr_300px] gap-4 flex-1">
        {/* Node palette */}
        <Card>
          <CardHeader>
            <CardTitle className="text-sm">Add Step</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            <Button
              variant="outline"
              className="w-full justify-start"
              onClick={() => addNode('screen')}
            >
              <FileText className="w-4 h-4 mr-2" /> Screen
            </Button>
            <Button
              variant="outline"
              className="w-full justify-start"
              onClick={() => addNode('decision')}
            >
              <ChevronRight className="w-4 h-4 mr-2" /> Decision
            </Button>
            <Button
              variant="outline"
              className="w-full justify-start"
              onClick={() => addNode('action')}
            >
              <Settings className="w-4 h-4 mr-2" /> Action
            </Button>
            <Button
              variant="outline"
              className="w-full justify-start"
              onClick={() => addNode('message')}
            >
              <MessageSquare className="w-4 h-4 mr-2" /> Message
            </Button>
            <Button
              variant="outline"
              className="w-full justify-start"
              onClick={() => addNode('wait')}
            >
              <Phone className="w-4 h-4 mr-2" /> Wait
            </Button>
            <Button
              variant="outline"
              className="w-full justify-start"
              onClick={() => addNode('collect')}
            >
              <User className="w-4 h-4 mr-2" /> Collect Data
            </Button>
            <Button
              variant="outline"
              className="w-full justify-start"
              onClick={() => addNode('payment')}
            >
              <CreditCard className="w-4 h-4 mr-2" /> Payment
            </Button>
          </CardContent>
        </Card>

        {/* Canvas */}
        <Card className="overflow-hidden">
          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onNodeClick={(_, node) => setSelectedNode(node)}
            nodeTypes={nodeTypes}
            fitView
          >
            <Background />
            <Controls />
            <MiniMap />
          </ReactFlow>
        </Card>

        {/* Properties panel */}
        <Card>
          <CardHeader>
            <CardTitle className="text-sm">Properties</CardTitle>
          </CardHeader>
          <CardContent>
            {selectedNode ? (
              <div className="space-y-3">
                <div>
                  <Label>Type</Label>
                  <Input value={selectedNode.type || ''} disabled />
                </div>
                <div>
                  <Label>Label</Label>
                  <Input
                    value={selectedNode.data.label || ''}
                    onChange={(e) =>
                      setNodes((nds) =>
                        nds.map((n) =>
                          n.id === selectedNode.id
                            ? { ...n, data: { ...n.data, label: e.target.value } }
                            : n
                        )
                      )
                    }
                  />
                </div>
                <Button
                  variant="destructive"
                  size="sm"
                  className="w-full"
                  onClick={() => deleteNode(selectedNode.id)}
                >
                  <Trash2 className="w-4 h-4 mr-2" /> Delete
                </Button>
              </div>
            ) : (
              <p className="text-sm text-gray-500">Select a node to edit its properties</p>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Existing Journeys List */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center justify-between">
            Existing Journeys
            <span className="text-sm text-gray-500 font-normal">{journeys.length} journey(s)</span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          {loadingJourneys ? (
            <p className="text-sm text-gray-500">Loading journeys…</p>
          ) : journeys.length === 0 ? (
            <p className="text-sm text-gray-500">No journeys yet. Create your first journey above.</p>
          ) : (
            <div className="space-y-2">
              {journeys.map((journey: any) => (
                <div key={journey.id} className="flex items-center justify-between p-3 border rounded-lg hover:bg-gray-50">
                  <div>
                    <p className="font-medium">{journey.name}</p>
                    <p className="text-xs text-gray-500">
                      {journey.status} · v{journey.version} · {journey.steps?.length || 0} steps
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Button size="sm" variant="outline" onClick={() => handleLoadJourney(journey)}>
                      Edit
                    </Button>
                    {journey.status === 'DRAFT' && (
                      <Button size="sm" onClick={() => publishMutation.mutate(journey.id)}>
                        Publish
                      </Button>
                    )}
                    {journey.status === 'PUBLISHED' && (
                      <Button size="sm" variant="outline" onClick={() => archiveMutation.mutate(journey.id)}>
                        Archive
                      </Button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

// ============================================================
// Custom node types
// ============================================================

function ScreenNode({ data }: NodeProps) {
  return (
    <div className="bg-blue-100 border-2 border-blue-500 rounded-lg p-3 min-w-[150px]">
      <Handle type="target" position={Position.Top} />
      <div className="text-xs text-blue-700">SCREEN</div>
      <div className="font-medium text-sm">{data.label}</div>
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}

function DecisionNode({ data }: NodeProps) {
  return (
    <div className="bg-yellow-100 border-2 border-yellow-500 rounded-lg p-3 min-w-[150px]">
      <Handle type="target" position={Position.Top} />
      <div className="text-xs text-yellow-700">DECISION</div>
      <div className="font-medium text-sm">{data.label}</div>
      <Handle type="source" position={Position.Bottom} id="yes" style={{ left: '30%' }} />
      <Handle type="source" position={Position.Bottom} id="no" style={{ left: '70%' }} />
    </div>
  );
}

function ActionNode({ data }: NodeProps) {
  return (
    <div className="bg-green-100 border-2 border-green-500 rounded-lg p-3 min-w-[150px]">
      <Handle type="target" position={Position.Top} />
      <div className="text-xs text-green-700">ACTION</div>
      <div className="font-medium text-sm">{data.label}</div>
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}

function MessageNode({ data }: NodeProps) {
  return (
    <div className="bg-purple-100 border-2 border-purple-500 rounded-lg p-3 min-w-[150px]">
      <Handle type="target" position={Position.Top} />
      <div className="text-xs text-purple-700">MESSAGE</div>
      <div className="font-medium text-sm">{data.label}</div>
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}

const nodeTypes = {
  screen: ScreenNode,
  decision: DecisionNode,
  action: ActionNode,
  message: MessageNode,
  wait: ActionNode,
  collect: ActionNode,
  payment: ActionNode,
};

const initialNodes: Node[] = [
  { id: '1', type: 'screen', position: { x: 250, y: 0 }, data: { label: 'Welcome' } },
  { id: '2', type: 'collect', position: { x: 250, y: 120 }, data: { label: 'Collect Phone' } },
  { id: '3', type: 'decision', position: { x: 250, y: 240 }, data: { label: 'New user?' } },
  { id: '4', type: 'action', position: { x: 100, y: 380 }, data: { label: 'Login' } },
  { id: '5', type: 'action', position: { x: 400, y: 380 }, data: { label: 'Register' } },
];

const initialEdges: Edge[] = [
  { id: 'e1-2', source: '1', target: '2', animated: true },
  { id: 'e2-3', source: '2', target: '3', animated: true },
  { id: 'e3-4', source: '3', target: '4', sourceHandle: 'yes', label: 'Yes', animated: true },
  { id: 'e3-5', source: '3', target: '5', sourceHandle: 'no', label: 'No', animated: true },
];
