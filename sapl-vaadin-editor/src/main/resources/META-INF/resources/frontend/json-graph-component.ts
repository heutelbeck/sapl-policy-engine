// D3.js-based JSON visualization with hierarchical tree layout

import { html, LitElement, css } from 'lit';
import { customElement, property, query } from 'lit/decorators.js';
import * as d3 from 'd3';

interface TreeNode extends d3.HierarchyPointNode<any> {
    data: {
        name: string;
        value: any;
        type: string;
        fullLabel: string;
    };
}

// Layout constants
const NODE_HEIGHT_SPACING = 60;
const NODE_WIDTH_SPACING = 180;
const NODE_SEPARATION_SIBLING = 0.8;
const NODE_SEPARATION_OTHER = 1;
const LINK_HORIZONTAL_OFFSET = 90;
const BOUNDS_PADDING_WIDTH = 200;
const BOUNDS_PADDING_HEIGHT = 100;

// Node styling constants
const NODE_WIDTH = 140;
const NODE_HEIGHT = 44;
const NODE_RADIUS = 3;
const TEXT_MAX_LENGTH = 20;

// Color scheme matching Vaadin Lumo dark theme
const COLORS = {
    background: 'hsl(210, 10%, 12%)',
    link: 'rgba(255, 255, 255, 0.1)',
    nodes: {
        object: { fill: 'hsl(210, 15%, 18%)', border: 'rgba(64, 160, 159, 0.6)', text: 'rgb(64, 160, 159)' },
        array: { fill: 'hsl(39, 30%, 25%)', border: 'rgba(255, 160, 64, 0.6)', text: 'rgb(255, 180, 100)' },
        string: { fill: 'hsl(145, 30%, 20%)', border: 'rgba(69, 140, 99, 0.7)', text: 'rgb(85, 170, 119)' },
        number: { fill: 'hsl(179, 30%, 25%)', border: 'rgba(64, 160, 159, 0.8)', text: 'rgb(100, 200, 200)' },
        boolean: { fill: 'hsl(280, 30%, 25%)', border: 'rgba(180, 120, 200, 0.6)', text: 'rgb(200, 140, 220)' },
        null: { fill: 'hsl(210, 8%, 16%)', border: 'rgba(255, 255, 255, 0.2)', text: 'rgba(255, 255, 255, 0.4)' }
    }
};

/**
 * Web component for interactive JSON graph visualization using D3.js.
 * Renders JSON data as a hierarchical tree with pan/zoom capabilities.
 */
@customElement('json-graph-visualization')
export class JsonGraphComponent extends LitElement {
    @property({ type: String })
    jsonData: string = '{}';

    @property({ type: Boolean, attribute: 'hide-maximize-button' })
    hideMaximizeButton: boolean = false;

    @property({ type: Boolean, attribute: 'dialog-open' })
    dialogOpen: boolean = false;

    @property({ type: String })
    initialTransform: string = '';

    @query('#graph-container')
    private container!: HTMLDivElement;

    private svg: any = null;
    private zoomBehavior: any = null;
    private resizeObserver: ResizeObserver | null = null;
    private currentTransform: any = null;

    static styles = css`
        :host {
            display: block;
            width: 100%;
            height: 100%;
            position: relative;
        }

        #graph-container {
            width: 100%;
            height: 100%;
            background: hsl(210, 10%, 12%);
            border-radius: 4px;
            overflow: hidden;
            position: relative;
        }

        .maximize-button {
            position: absolute;
            top: 10px;
            right: 10px;
            z-index: 1;
            background: rgba(64, 160, 159, 0.15);
            border: 1px solid rgba(64, 160, 159, 0.4);
            border-radius: 3px;
            padding: 6px 10px;
            color: rgb(64, 160, 159);
            cursor: pointer;
            font-size: 14px;
            transition: all 0.2s;
            pointer-events: auto;
        }
        
        :host([dialog-open]) .maximize-button {
            display: none;
        }

        .maximize-button:hover {
            background: rgba(64, 160, 159, 0.25);
            border-color: rgb(64, 160, 159);
            color: rgb(64, 160, 159);
        }

        .maximize-button:active {
            background: rgba(64, 160, 159, 0.35);
        }
    `;

    render() {
        return html`
            <div id="graph-container">
                ${!this.hideMaximizeButton && !this.dialogOpen ? html`
                    <button class="maximize-button" @click=${this.handleMaximizeClick}>
                        ◰
                    </button>
                ` : ''}
            </div>
        `;
    }

    firstUpdated() {
        setTimeout(() => this.initializeGraph(), 100);
        this.observeResize();
    }

    updated(changedProperties: Map<string, any>) {
        if (changedProperties.has('jsonData')) {
            this.renderGraph();
        }

        if (changedProperties.has('initialTransform') && this.initialTransform && this.svg) {
            setTimeout(() => this.applyInitialTransform(), 150);
        }
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        this.resizeObserver?.disconnect();
    }

    private handleMaximizeClick(event: MouseEvent) {
        event.preventDefault();
        event.stopPropagation();

        this.dispatchEvent(new CustomEvent('maximize-clicked', {
            bubbles: true,
            composed: true
        }));
    }

    private observeResize() {
        this.resizeObserver = new ResizeObserver(() => this.handleResize());
        this.resizeObserver.observe(this.container);
    }

    private handleResize() {
        if (!this.svg) return;

        const width = this.container.clientWidth;
        const height = this.container.clientHeight;

        this.svg.attr('width', width).attr('height', height);
        this.renderGraph();
    }

    private initializeGraph() {
        if (!this.container) return;

        const width = this.container.clientWidth || 800;
        const height = this.container.clientHeight || 600;

        d3.select(this.container).select('svg').remove();

        this.svg = d3.select(this.container)
            .append('svg')
            .attr('width', width)
            .attr('height', height)
            .style('background', COLORS.background);

        const g = this.svg.append('g').attr('class', 'zoom-group');

        this.zoomBehavior = d3.zoom()
            .scaleExtent([0.1, 3])
            .on('zoom', (event) => {
                g.attr('transform', event.transform);
                this.currentTransform = event.transform;
            });

        this.svg.call(this.zoomBehavior);
        this.renderGraph();
    }

    private renderGraph() {
        if (!this.svg) return;

        try {
            const jsonObject = JSON.parse(this.jsonData || '{}');
            const g = this.svg.select('.zoom-group');
            g.selectAll('*').remove();

            const width = this.container.clientWidth || 800;
            const height = this.container.clientHeight || 600;

            const hierarchy = d3.hierarchy(this.jsonToHierarchy(jsonObject));
            const treeData = this.createTreeLayout(hierarchy);
            const bounds = this.calculateBounds(treeData);

            this.renderLinks(g, treeData);
            this.renderNodes(g, treeData);
            this.centerView(width, height, bounds);
        } catch (error) {
            // Invalid JSON - fail silently
        }
    }

    private createTreeLayout(hierarchy: d3.HierarchyNode<any>) {
        const treeLayout = d3.tree<any>()
            .nodeSize([NODE_HEIGHT_SPACING, NODE_WIDTH_SPACING])
            .separation((a, b) => a.parent === b.parent ? NODE_SEPARATION_SIBLING : NODE_SEPARATION_OTHER);

        return treeLayout(hierarchy);
    }

    private calculateBounds(treeData: d3.HierarchyPointNode<any>) {
        let minX = Infinity, maxX = -Infinity;
        let minY = Infinity, maxY = -Infinity;

        treeData.each((node: any) => {
            minX = Math.min(minX, node.x);
            maxX = Math.max(maxX, node.x);
            minY = Math.min(minY, node.y);
            maxY = Math.max(maxY, node.y);
        });

        return {
            width: maxY - minY + BOUNDS_PADDING_WIDTH,
            height: maxX - minX + BOUNDS_PADDING_HEIGHT,
            minX,
            minY
        };
    }

    private renderLinks(g: any, treeData: d3.HierarchyPointNode<any>) {
        g.append('g')
            .attr('class', 'links')
            .selectAll('path')
            .data(treeData.links())
            .join('path')
            .attr('d', (d: any) => this.createLinkPath(d))
            .attr('fill', 'none')
            .attr('stroke', COLORS.link)
            .attr('stroke-width', 1.5);
    }

    private createLinkPath(d: any): string {
        return `M${d.source.y},${d.source.x}
                L${d.source.y + LINK_HORIZONTAL_OFFSET},${d.source.x}
                L${d.source.y + LINK_HORIZONTAL_OFFSET},${d.target.x}
                L${d.target.y},${d.target.x}`;
    }

    private renderNodes(g: any, treeData: d3.HierarchyPointNode<any>) {
        const nodes = g.append('g')
            .attr('class', 'nodes')
            .selectAll('g')
            .data(treeData.descendants())
            .join('g')
            .attr('transform', (d: any) => `translate(${d.y},${d.x})`);

        this.renderNodeRectangles(nodes);
        this.renderNodeLabels(nodes);
    }

    private renderNodeRectangles(nodes: any) {
        nodes.append('rect')
            .attr('x', -NODE_WIDTH / 2)
            .attr('y', -NODE_HEIGHT / 2)
            .attr('width', NODE_WIDTH)
            .attr('height', NODE_HEIGHT)
            .attr('rx', NODE_RADIUS)
            .attr('fill', (d: any) => this.getNodeColors(d.data.type).fill)
            .attr('stroke', (d: any) => this.getNodeColors(d.data.type).border)
            .attr('stroke-width', 1.5)
            .style('cursor', 'pointer')
            .style('filter', 'drop-shadow(0 2px 4px rgba(0,0,0,0.3))');
    }

    private renderNodeLabels(nodes: any) {
        nodes.append('text')
            .attr('dy', '0.35em')
            .attr('text-anchor', 'middle')
            .style('fill', (d: any) => this.getNodeColors(d.data.type).text)
            .style('font-family', '"Segoe UI", Candara, "Bitstream Vera Sans", "DejaVu Sans", "Trebuchet MS", Verdana, sans-serif')
            .style('font-size', '12px')
            .style('font-weight', '400')
            .style('pointer-events', 'none')
            .text((d: any) => this.truncateText(d.data.fullLabel, TEXT_MAX_LENGTH))
            .append('title')
            .text((d: any) => d.data.fullLabel);
    }

    private centerView(width: number, height: number, bounds: any) {
        const offsetX = width / 2 - bounds.width / 2 - bounds.minY;
        const offsetY = height / 2 - bounds.height / 2 - bounds.minX;

        if (this.initialTransform) {
            this.applySavedTransform(offsetX, offsetY);
        } else {
            this.svg.call(this.zoomBehavior.transform,
                d3.zoomIdentity.translate(offsetX, offsetY));
        }
    }

    private applySavedTransform(offsetX: number, offsetY: number) {
        try {
            const transform = JSON.parse(this.initialTransform);
            const zoomTransform = d3.zoomIdentity
                .translate(offsetX, offsetY)
                .scale(transform.k);

            this.svg.call(this.zoomBehavior.transform, zoomTransform);
            this.currentTransform = zoomTransform;
            this.initialTransform = '';
        } catch {
            this.svg.call(this.zoomBehavior.transform,
                d3.zoomIdentity.translate(offsetX, offsetY));
        }
    }

    private jsonToHierarchy(obj: any, key: string = 'root'): any {
        const type = this.getValueType(obj);
        const label = this.formatLabel(key, obj, type);

        const node: any = {
            name: key,
            type: type,
            fullLabel: label,
            value: obj
        };

        if (typeof obj === 'object' && obj !== null) {
            node.children = Array.isArray(obj)
                ? obj.map((item, index) => this.jsonToHierarchy(item, `[${index}]`))
                : Object.entries(obj).map(([k, v]) => this.jsonToHierarchy(v, k));
        }

        return node;
    }

    private formatLabel(key: string, value: any, type: string): string {
        switch (type) {
            case 'object':
                return Object.keys(value).length > 0 ? `${key}` : `${key} {}`;
            case 'array':
                return `${key} [${value.length}]`;
            case 'string':
                return `${key}: "${value}"`;
            case 'null':
                return `${key}: null`;
            default:
                return `${key}: ${value}`;
        }
    }

    private truncateText(text: string, maxLength: number): string {
        return text.length <= maxLength ? text : text.substring(0, maxLength - 1) + '…';
    }

    private getValueType(value: any): string {
        if (value === null) return 'null';
        if (Array.isArray(value)) return 'array';
        return typeof value;
    }

    private getNodeColors(type: string) {
        return COLORS.nodes[type as keyof typeof COLORS.nodes] || COLORS.nodes.object;
    }

    getZoomTransform(): string {
        if (!this.currentTransform) {
            return JSON.stringify({ k: 1, x: 0, y: 0 });
        }
        return JSON.stringify({
            k: this.currentTransform.k,
            x: this.currentTransform.x,
            y: this.currentTransform.y
        });
    }

    applyInitialTransform() {
        if (!this.initialTransform || !this.svg) return;

        try {
            const transform = JSON.parse(this.initialTransform);
            const g = this.svg.select('.zoom-group');
            const bounds = g.node()?.getBBox();

            if (bounds) {
                const width = this.container.clientWidth || 800;
                const height = this.container.clientHeight || 600;
                const offsetX = width / 2 - (bounds.x + bounds.width / 2);
                const offsetY = height / 2 - (bounds.y + bounds.height / 2);

                const zoomTransform = d3.zoomIdentity
                    .translate(offsetX, offsetY)
                    .scale(transform.k);

                this.svg.call(this.zoomBehavior.transform, zoomTransform);
                this.currentTransform = zoomTransform;
            }

            this.initialTransform = '';
        } catch {
            // Invalid transform - ignore
        }
    }
}