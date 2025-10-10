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

interface FormattedLabel {
    lines: string[];
    needsTruncation: boolean;
    height: number;
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
const NODE_HEIGHT_BASE = 44;
const NODE_HEIGHT_PER_LINE = 18;
const NODE_RADIUS = 3;
const TEXT_MAX_LENGTH = 40;
const LINE_CHAR_LIMIT = 25;
const MAX_DISPLAY_LINES = 2;
const LINE_HEIGHT = 16;

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
 * Features multi-line text, smart truncation, and click-to-copy functionality.
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
    private tooltip: any = null;

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

        .custom-tooltip {
            position: absolute;
            visibility: hidden;
            background: rgba(0, 0, 0, 0.92);
            color: #fff;
            padding: 10px 14px;
            border-radius: 6px;
            font-size: 12px;
            font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
            max-width: 350px;
            word-wrap: break-word;
            z-index: 10000;
            pointer-events: none;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
            border: 1px solid rgba(64, 160, 159, 0.4);
            line-height: 1.4;
        }

        .custom-tooltip.visible {
            visibility: visible;
        }

        .tooltip-type {
            font-weight: 600;
            color: rgb(64, 160, 159);
            text-transform: uppercase;
            font-size: 10px;
            letter-spacing: 0.5px;
            margin-bottom: 6px;
        }

        .tooltip-content {
            margin-top: 6px;
            font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
            white-space: pre-wrap;
            word-break: break-word;
        }

        .tooltip-hint {
            margin-top: 8px;
            font-size: 10px;
            opacity: 0.7;
            font-style: italic;
            border-top: 1px solid rgba(255, 255, 255, 0.1);
            padding-top: 6px;
        }

        .tooltip-copied {
            color: rgb(85, 170, 119);
            font-weight: 600;
        }

        .node-text-group text {
            font-weight: 400;
            text-rendering: optimizeLegibility;
            user-select: none;
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

        this.tooltip = this.createTooltip();

        this.renderGraph();
    }

    private createTooltip(): any {
        return d3.select(this.container)
            .append('div')
            .attr('class', 'custom-tooltip');
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
        const self = this;

        nodes.append('rect')
            .attr('x', -NODE_WIDTH / 2)
            .attr('y', (d: any) => {
                const formatted = this.formatNodeLabel(d.data.fullLabel);
                return -formatted.height / 2;
            })
            .attr('width', NODE_WIDTH)
            .attr('height', (d: any) => {
                const formatted = this.formatNodeLabel(d.data.fullLabel);
                return formatted.height;
            })
            .attr('rx', NODE_RADIUS)
            .attr('fill', (d: any) => this.getNodeColors(d.data.type).fill)
            .attr('stroke', (d: any) => this.getNodeColors(d.data.type).border)
            .attr('stroke-width', 1.5)
            .style('cursor', 'pointer')
            .style('filter', 'drop-shadow(0 2px 4px rgba(0,0,0,0.3))')
            .on('mouseenter', function(event: MouseEvent, d: any) {
                self.showTooltip(event, d);
            })
            .on('mousemove', function(event: MouseEvent) {
                self.moveTooltip(event);
            })
            .on('mouseleave', function() {
                self.hideTooltip();
            })
            .on('click', function(event: MouseEvent, d: any) {
                self.copyToClipboard(d.data.fullLabel);
            });
    }

    private renderNodeLabels(nodes: any) {
        nodes.each((d: any, i: number, nodeElements: any) => {
            const node = d3.select(nodeElements[i]);
            const formatted = this.formatNodeLabel(d.data.fullLabel);

            const textGroup = node.append('g')
                .attr('class', 'node-text-group');

            formatted.lines.forEach((line, idx) => {
                const yOffset = (idx - (formatted.lines.length - 1) / 2) * LINE_HEIGHT;

                textGroup.append('text')
                    .attr('dy', `${yOffset}px`)
                    .attr('text-anchor', 'middle')
                    .style('fill', this.getNodeColors(d.data.type).text)
                    .style('font-family', '"Segoe UI", Candara, "Bitstream Vera Sans", "DejaVu Sans", "Trebuchet MS", Verdana, sans-serif')
                    .style('font-size', '11px')
                    .style('font-weight', '400')
                    .style('pointer-events', 'none')
                    .text(line + (idx === formatted.lines.length - 1 && formatted.needsTruncation ? '…' : ''));
            });
        });
    }

    private formatNodeLabel(text: string): FormattedLabel {
        if (text.length <= LINE_CHAR_LIMIT) {
            return {
                lines: [text],
                needsTruncation: false,
                height: NODE_HEIGHT_BASE
            };
        }

        const lines: string[] = [];
        let remainingText = text;

        for (let i = 0; i < MAX_DISPLAY_LINES && remainingText.length > 0; i++) {
            if (remainingText.length <= LINE_CHAR_LIMIT) {
                lines.push(remainingText);
                remainingText = '';
            } else {
                let breakPoint = LINE_CHAR_LIMIT;
                const segment = remainingText.substring(0, LINE_CHAR_LIMIT + 1);
                const lastSpace = segment.lastIndexOf(' ');
                const lastColon = segment.lastIndexOf(':');
                const lastComma = segment.lastIndexOf(',');
                const lastDot = segment.lastIndexOf('.');

                const breakChars = [lastSpace, lastColon, lastComma, lastDot]
                    .filter(pos => pos > LINE_CHAR_LIMIT * 0.6);

                if (breakChars.length > 0) {
                    breakPoint = Math.max(...breakChars) + 1;
                }

                lines.push(remainingText.substring(0, breakPoint).trim());
                remainingText = remainingText.substring(breakPoint).trim();
            }
        }

        const needsTruncation = remainingText.length > 0;
        const height = NODE_HEIGHT_BASE + (lines.length - 1) * NODE_HEIGHT_PER_LINE;

        return { lines, needsTruncation, height };
    }

    private showTooltip(event: MouseEvent, d: any) {
        const formatted = this.formatNodeLabel(d.data.fullLabel);

        if (!formatted.needsTruncation && d.data.fullLabel.length <= LINE_CHAR_LIMIT) {
            return;
        }

        this.tooltip
            .classed('visible', true)
            .html(`
                <div class="tooltip-type">${d.data.type}</div>
                <div class="tooltip-content">${this.escapeHtml(d.data.fullLabel)}</div>
                <div class="tooltip-hint">Click node to copy</div>
            `);

        this.moveTooltip(event);
    }

    private moveTooltip(event: MouseEvent) {
        const tooltipNode = this.tooltip.node();
        if (!tooltipNode || !this.tooltip.classed('visible')) return;

        const tooltipWidth = tooltipNode.offsetWidth;
        const tooltipHeight = tooltipNode.offsetHeight;
        const containerRect = this.container.getBoundingClientRect();

        let left = event.clientX - containerRect.left + 15;
        let top = event.clientY - containerRect.top + 15;

        if (left + tooltipWidth > containerRect.width) {
            left = event.clientX - containerRect.left - tooltipWidth - 15;
        }

        if (top + tooltipHeight > containerRect.height) {
            top = event.clientY - containerRect.top - tooltipHeight - 15;
        }

        this.tooltip
            .style('top', `${top}px`)
            .style('left', `${left}px`);
    }

    private hideTooltip() {
        this.tooltip.classed('visible', false);
    }

    private copyToClipboard(text: string) {
        if (!navigator.clipboard) {
            return;
        }

        navigator.clipboard.writeText(text).then(() => {
            this.tooltip
                .classed('visible', true)
                .html('<div class="tooltip-copied">✓ Copied to clipboard!</div>');

            setTimeout(() => {
                this.tooltip.classed('visible', false);
            }, 1500);
        }).catch(() => {
            // Clipboard write failed - ignore silently
        });
    }

    private escapeHtml(text: string): string {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
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